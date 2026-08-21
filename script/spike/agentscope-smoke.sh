#!/usr/bin/env bash
# NexAI 智能体平台 · 工单 01 集成 spike 冒烟脚本（spec Testing Decisions S5）
#
# 用途：验证 agentscope-java 嵌入 nexai-server 后的三大集成风险（Jackson 双栈 / SSE 真实事件流 /
#       会话状态存取）。不进 CI——依赖真实模型凭据与远端 PG/Redis。
#
# 前置：
#   1. nexai-server 已启动（默认 48080；老 yudao 实例占 48080 时可用 --server.port=48081）
#   2. 启动进程的环境变量中有凭据（不入库不入 git，仅经环境变量注入）：
#        export SPIKE_AI_API_KEY=sk-...                       # 必填（OpenAI 兼容 key）
#        export SPIKE_AI_BASE_URL=https://api.deepseek.com    # 可选
#        export SPIKE_AI_MODEL=deepseek-v4-flash              # 可选
#   3. 本脚本自身只需：export NEXAI_BASE=http://localhost:48080   # 可选
#
# 生命周期：M1 工单 06 建立正式运行时装配后，本脚本随 /admin-api/ai/spike/** 端点一并删除。
set -euo pipefail

BASE="${NEXAI_BASE:-http://localhost:48080}"
TENANT=(-H "tenant-id: 1")   # 多租户过滤器要求携带租户编号（spike 端点仅临时放行认证）
SID="smoke-$(date +%s)"

echo "===== 1/4 Jackson 双栈对比（AgentEvent 经 Jackson 2 与 Jackson 3） ====="
curl -sf "${TENANT[@]}" "$BASE/admin-api/ai/spike/jackson" | python3 -m json.tool

echo "===== 2/4 状态存取冒烟（PostgresAgentStateStore） ====="
curl -sf "${TENANT[@]}" "$BASE/admin-api/ai/spike/state?store=pg" | python3 -m json.tool

echo "===== 3/4 状态存取冒烟（RedisAgentStateStore / 共享 RedissonClient） ====="
curl -sf "${TENANT[@]}" "$BASE/admin-api/ai/spike/state?store=redis" | python3 -m json.tool

echo "===== 4/4 SSE 两轮真实对话：同 sessionId 跨请求验证记忆恢复（sessionId=${SID}） ====="
# 解析 SSE：打印事件类型序列 + 拼接文本增量 + token 用量
parse_sse() {
    python3 -c '
import sys, json
types, text, usage = [], "", None
for line in sys.stdin:
    line = line.strip()
    if not line.startswith("data:"):
        continue
    payload = line[5:].strip()
    if not payload:
        continue
    ev = json.loads(payload)
    t = ev.get("type")
    if t not in types:
        types.append(t)
    if t == "TEXT_BLOCK_DELTA":
        text += ev.get("delta", "")
    if t == "MODEL_CALL_END":
        usage = ev.get("usage")
print("  事件类型序列:", " -> ".join(types))
print("  拼接文本:", text)
print("  token 用量:", usage)
'
}

for prompt in "我叫小明，请记住我。用一句话回答。" "我叫什么名字？"; do
    encoded=$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "$prompt")
    echo "--- prompt: $prompt"
    curl -sN "${TENANT[@]}" --max-time 120 \
        "$BASE/admin-api/ai/spike/sse?store=pg&sessionId=$SID&prompt=$encoded" | parse_sse
done

echo "===== 冒烟完成（第二轮回答应包含第一轮告知的名字，即状态跨请求恢复成功） ====="
