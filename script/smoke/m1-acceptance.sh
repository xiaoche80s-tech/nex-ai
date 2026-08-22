#!/usr/bin/env bash
# NexAI 智能体平台 · 工单 09 M1 集成验收冒烟脚本（spec Testing Decisions S5，接替已删除的 spike 冒烟）
#
# 用途：经正式 REST + SSE API（/admin-api/ai/**）端到端跑通 M1 最小闭环：
#       配渠道 → 测连通 → 建模型 → 写规格 → 发布版本 → 调试会话多轮对话（跨请求记忆恢复）
#       → 中断运行中的流 → 克隆重跑（历史复制）→ 提交问题反馈 → 管理员流转闭环。
#       不进 CI——依赖运行中的 nexai-server 与（可选的）真实模型凭据。
#
# 两种模式：
#   1) mock 模式（默认）：脚本自起 fake-openai-server.py（OpenAI 兼容 SSE mock），
#      验证后端真实 HTTP 调用链、事件流序列、状态存储记忆恢复与全部管理面接口。
#      无需任何凭据，随时可重跑。
#   2) real 模式：export M1_SMOKE_MODE=real 并提供真实凭据（OpenAI 兼容渠道）：
#        export M1_AI_BASE_URL=https://api.deepseek.com
#        export M1_AI_API_KEY=<你的密钥>
#        export M1_AI_MODEL=deepseek-chat
#      注意：真实模型对「我叫什么名字」的回答不保证复述名字，real 模式下该断言自动放宽
#      为「流正常收尾且回复非空」。
#
# 前置：
#   1. 最新构建的 nexai-server 已启动（默认 48080；可用 NEXAI_BASE 覆盖）
#   2. 登录账号可用（默认 admin / admin123，可用 NEXAI_USERNAME / NEXAI_PASSWORD 覆盖）
#   3. python3 可用（JSON / SSE 解析）
set -euo pipefail

BASE="${NEXAI_BASE:-http://localhost:48080}"
USERNAME="${NEXAI_USERNAME:-admin}"
PASSWORD="${NEXAI_PASSWORD:-admin123}"
TENANT="${NEXAI_TENANT:-1}"
MODE="${M1_SMOKE_MODE:-mock}"
MOCK_PORT="${M1_MOCK_PORT:-18080}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MOCK_PID=""

cleanup() { [ -n "$MOCK_PID" ] && kill "$MOCK_PID" 2>/dev/null || true; }
trap cleanup EXIT

die() { echo; echo "❌ 验收失败：$*" >&2; exit 1; }
step() { echo; echo "===== $* ====="; }

# 从 stdin 的 JSON 提取字段，用法：echo "$json" | jget "['data']['id']"
jget() { python3 -c "import sys, json; print(json.load(sys.stdin)$1)"; }

api() { # method path [json_body] -> 响应原文（curl -sf，非 2xx 直接失败）
    local method="$1" path="$2" body="${3:-}"
    local args=(curl -sf --max-time 90 -X "$method" "$BASE$path"
        -H "tenant-id: $TENANT" -H "Authorization: Bearer $TOKEN"
        -H "Content-Type: application/json")
    [ -n "$body" ] && args+=(-d "$body")
    "${args[@]}"
}

# 消费调试会话 SSE 流并断言（python 内完成）：
#   $1=会话ID $2=消息内容 $3=期望回复包含的子串（空串则只断言流健康）
consume_sse() { # $1=会话ID $2=消息内容 $3=期望回复子串（空则只断言流健康）
    local sid="$1" content="$2" expect="${3:-}"
    curl -sN --max-time 120 -X POST "$BASE/admin-api/ai/session/$sid/message" \
        -H "tenant-id: $TENANT" -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" -d "{\"content\":$(python3 -c 'import json,sys; print(json.dumps(sys.argv[1], ensure_ascii=False))' "$content")}" \
        | python3 "$SCRIPT_DIR/sse-consumer.py" "$expect"
}

echo "M1 集成验收冒烟（模式：${MODE}，目标：${BASE}）"

# ---------- 0. 凭据来源 ----------
if [ "$MODE" = "mock" ]; then
    step "0/9 自起 OpenAI 兼容 mock 模型服务（127.0.0.1:${MOCK_PORT}）"
    python3 "$SCRIPT_DIR/fake-openai-server.py" "$MOCK_PORT" >/tmp/m1-fake-openai.log 2>&1 &
    MOCK_PID=$!
    for _ in $(seq 1 20); do
        nc -z 127.0.0.1 "$MOCK_PORT" 2>/dev/null && break
        sleep 0.3
    done
    nc -z 127.0.0.1 "$MOCK_PORT" 2>/dev/null || die "mock 模型服务启动失败（见 /tmp/m1-fake-openai.log）"
    AI_BASE_URL="http://127.0.0.1:$MOCK_PORT"
    AI_API_KEY="${M1_MOCK_KEY:-local-smoke-no-auth}"   # mock 服务不校验密钥，占位值可经环境变量覆盖
    AI_MODEL="fake-model"
else
    : "${M1_AI_BASE_URL:?real 模式需要 M1_AI_BASE_URL（OpenAI 兼容端点）}"
    : "${M1_AI_API_KEY:?real 模式需要 M1_AI_API_KEY}"
    : "${M1_AI_MODEL:?real 模式需要 M1_AI_MODEL}"
    AI_BASE_URL="$M1_AI_BASE_URL"; AI_API_KEY="$M1_AI_API_KEY"; AI_MODEL="$M1_AI_MODEL"
fi
echo "  模型端点: $AI_BASE_URL  模型: $AI_MODEL"

# ---------- 1. 登录 ----------
step "1/9 登录管理后台（${USERNAME}）"
TOKEN=$(curl -sf -X POST "$BASE/admin-api/system/auth/login" -H "tenant-id: $TENANT" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" | jget "['data']['accessToken']") \
    || die "登录失败（检查账号密码与验证码开关 nexai.captcha.enable=false）"
echo "  accessToken: ${TOKEN:0:20}..."

TS=$(date +%s)

# ---------- 2. 渠道：创建 + 表单凭据连通性（US7 保存前验证） ----------
step "2/9 创建模型渠道 + 保存前连通性测试"
CONN=$(api POST /admin-api/ai/channel/test-connectivity \
    "{\"provider\":\"openai-compat\",\"baseUrl\":\"$AI_BASE_URL\",\"apiKey\":\"$AI_API_KEY\",\"modelId\":\"$AI_MODEL\"}") \
    || die "连通性测试请求失败"
[ "$(echo "$CONN" | jget "['data']['success']")" = "True" ] \
    || die "连通性测试未通过: $(echo "$CONN" | jget "['data']['message']")"
echo "  连通性 OK（耗时 $(echo "$CONN" | jget "['data']['durationMs']")ms）"

CHANNEL_ID=$(api POST /admin-api/ai/channel/create \
    "{\"name\":\"M1验收渠道-$TS\",\"provider\":\"openai-compat\",\"baseUrl\":\"$AI_BASE_URL\",\"apiKey\":\"$AI_API_KEY\"}" \
    | jget "['data']") || die "创建渠道失败"
echo "  渠道编号: $CHANNEL_ID"

# ---------- 3. 模型：创建 ----------
step "3/9 登记模型"
MODEL_ID=$(api POST /admin-api/ai/model/create \
    "{\"channelId\":$CHANNEL_ID,\"modelId\":\"m1smoke-$TS\",\"name\":\"M1验收模型-$TS\",\"contextWindow\":128000,\"inputPrice\":0.5,\"outputPrice\":2.0,\"capabilities\":[\"chat\"]}" \
    | jget "['data']") || die "创建模型失败"
echo "  模型编号: ${MODEL_ID}（登记标识 m1smoke-${TS}；探测与调用使用端点真实标识 ${AI_MODEL}）"

# ---------- 4. 规格：创建 + 发布（不可变版本） ----------
step "4/9 创建智能体规格并发布版本"
SPEC_ID=$(api POST /admin-api/ai/spec/create \
    "{\"name\":\"M1验收规格-$TS\",\"description\":\"M1 集成验收冒烟自动创建\",\"modelId\":$MODEL_ID,\"systemPrompt\":\"你是 M1 验收演示助手，回答简洁。\",\"maxIters\":5,\"temperature\":0.7}" \
    | jget "['data']") || die "创建规格失败"
VERSION_NO=$(api POST /admin-api/ai/spec/publish "{\"id\":$SPEC_ID,\"remark\":\"M1 验收冒烟发布\"}" | jget "['data']") \
    || die "发布版本失败"
echo "  规格编号: ${SPEC_ID}，发布版本号: v$VERSION_NO"

# ---------- 5. 调试会话：两轮对话验证跨请求记忆恢复 ----------
step "5/9 创建调试会话，两轮对话验证跨请求记忆恢复（状态存储）"
SESSION_ID=$(api POST /admin-api/ai/session/debug/create \
    "{\"specId\":$SPEC_ID,\"title\":\"M1验收会话-$TS\"}" | jget "['data']") || die "创建调试会话失败"
echo "  会话编号: $SESSION_ID"

echo "-- 第 1 轮"
consume_sse "$SESSION_ID" "我叫小明，请记住我。" "记住了"

echo "-- 第 2 轮（新 HTTP 请求；历史恢复依赖 PostgresAgentStateStore）"
if [ "$MODE" = "mock" ]; then
    consume_sse "$SESSION_ID" "我叫什么名字？" "你叫小明"
else
    consume_sse "$SESSION_ID" "我叫什么名字？" ""
fi

# ---------- 6. 中断正在运行的流 ----------
step "6/9 中断正在运行的事件流"
curl -sN --max-time 120 -X POST "$BASE/admin-api/ai/session/$SESSION_ID/message" \
    -H "tenant-id: $TENANT" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d '{"content":"请给我一段长回复"}' >/tmp/m1-interrupt-stream.txt &
STREAM_PID=$!
sleep 1.5
INTR=$(api POST "/admin-api/ai/session/$SESSION_ID/interrupt" "") || die "中断请求失败"
[ "$(echo "$INTR" | jget "['data']")" = "True" ] || echo "  ⚠️ 中断返回 false（流可能已结束，时序敏感，非硬失败）"
wait "$STREAM_PID" || true
python3 - <<'PYEOF' || die "中断后事件流未正常收尾"
import json
types = []
for line in open("/tmp/m1-interrupt-stream.txt"):
    line = line.strip()
    if line.startswith("data:"):
        payload = line[5:].strip()
        if payload and payload != "[DONE]":
            t = json.loads(payload).get("type")
            if t not in types:
                types.append(t)
print("  中断后事件序列:", " -> ".join(types))
assert "AGENT_END" in types, "中断后缺少 AGENT_END"
PYEOF
INTR2=$(api POST "/admin-api/ai/session/$SESSION_ID/interrupt" "") || true
echo "  幂等重发中断返回: ${INTR2}（无运行中流时应为 false）"

# ---------- 7. 克隆重跑（历史复制 + 参数微调） ----------
step "7/9 克隆会话并微调参数重跑（历史应被复制）"
CLONE_ID=$(api POST "/admin-api/ai/session/$SESSION_ID/clone" \
    "{\"title\":\"M1验收克隆-$TS\",\"temperature\":0.1}" | jget "['data']") || die "克隆失败"
echo "  克隆会话编号: ${CLONE_ID}（override temperature=0.1）"
DETAIL=$(api GET "/admin-api/ai/session/page?pageNo=1&pageSize=10" | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print(next((s['overrideTemperature'] for s in d['data']['list'] if s['id']==$CLONE_ID), 'MISSING'))")
[ "$DETAIL" = "0.1" ] || die "克隆会话的参数覆盖未生效（overrideTemperature=${DETAIL}）"
echo "-- 克隆会话追问（历史已复制，应仍知道名字）"
if [ "$MODE" = "mock" ]; then
    consume_sse "$CLONE_ID" "我叫什么名字？" "你叫小明"
else
    consume_sse "$CLONE_ID" "我叫什么名字？" ""
fi

# ---------- 8. 问题反馈：提交（关联会话） ----------
step "8/9 提交问题反馈（关联会话 ${SESSION_ID}）"
FEEDBACK_ID=$(api POST /admin-api/ai/feedback/create \
    "{\"content\":\"M1 验收冒烟自动提交：事件流与记忆恢复正常，此条用于验证反馈闭环。\",\"screenshotUrls\":[],\"sessionId\":\"$SESSION_ID\"}" \
    | jget "['data']") || die "创建反馈失败"
echo "  反馈编号: $FEEDBACK_ID"

# ---------- 9. 管理员流转闭环 ----------
step "9/9 管理员流转反馈状态（待处理 → 处理中 → 已解决）"
api PUT /admin-api/ai/feedback/transition "{\"id\":$FEEDBACK_ID,\"targetStatus\":20}" >/dev/null || die "流转到处理中失败"
api PUT /admin-api/ai/feedback/transition "{\"id\":$FEEDBACK_ID,\"targetStatus\":30}" >/dev/null || die "流转到已解决失败"
STATUS=$(api GET "/admin-api/ai/feedback/get?id=$FEEDBACK_ID" | jget "['data']['status']")
[ "$STATUS" = "30" ] || die "反馈终态不是已解决（status=${STATUS}）"
echo "  反馈终态: 已解决（30）✓"

echo
echo "✅ M1 集成验收冒烟全流程通过（模式：${MODE}）"
echo "   本轮创建：渠道 $CHANNEL_ID / 模型 $MODEL_ID / 规格 $SPEC_ID v$VERSION_NO / 会话 $SESSION_ID + 克隆 $CLONE_ID / 反馈 $FEEDBACK_ID"
echo "   （演示数据保留在租户 ${TENANT}，可在管理界面查看；HITL 三态确认由 S1 自动化测试覆盖——M1 生产链路无触发 ASK 的工具）"
