#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""sse-consumer —— M1 验收冒烟的调试会话 SSE 流解析器（stdin → 断言 → 摘要）。

独立成文件的原因：bash 中 `curl | python3 - <<EOF` 的 heredoc 会抢占 stdin，
管道数据丢失；解析器必须以文件形式运行，stdin 才能留给事件流。

用法：curl -N ... | sse-consumer.py [期望回复子串] [--min-input-tokens N]
      sse-consumer.py [期望子串] < 事件流文件   （shell 重定向，解析器不读文件系统）
参数：
  期望子串   空串跳过该断言
  --min-input-tokens N  断言 MODEL_CALL_END.usage.inputTokens ≥ N
                        （跨轮历史累积的模型无关指标：第二轮的输入 token 必然
                          大于第一轮，real 模式下可替代「复述名字」验证记忆恢复）
退出码：断言失败（SESSION_ERROR / 缺收尾事件 / 空回复 / 期望子串未命中 / token 下限）非零。
"""
import json
import sys

expect = ""
min_input_tokens = None
args = sys.argv[1:]
i = 0
while i < len(args):
    if args[i] == "--min-input-tokens":
        min_input_tokens = int(args[i + 1])
        i += 2
    else:
        expect = args[i]
        i += 1

types, text, usage = [], "", None
for line in sys.stdin:
    line = line.strip()
    if not line.startswith("data:"):
        continue
    payload = line[5:].strip()
    if not payload or payload == "[DONE]":
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
print("  拼接回复:", text[:120] + ("..." if len(text) > 120 else ""))
print("  token 用量:", usage)

errors = []
if "SESSION_ERROR" in types:
    errors.append("事件流以 SESSION_ERROR 收尾")
if "AGENT_START" not in types or "AGENT_END" not in types:
    errors.append("事件流缺少 AGENT_START/AGENT_END 完整收尾")
if not text.strip():
    errors.append("回复文本为空")
if expect and expect not in text:
    errors.append("回复未包含期望子串「%s」" % expect)
if min_input_tokens is not None:
    got = (usage or {}).get("inputTokens")
    if got is None or got < min_input_tokens:
        errors.append("输入 token=%s 未达下限 %d（跨轮历史未累积？）" % (got, min_input_tokens))
if errors:
    print("❌ SSE 断言失败：" + "；".join(errors), file=sys.stderr)
    sys.exit(1)
