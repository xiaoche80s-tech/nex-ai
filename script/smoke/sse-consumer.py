#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""sse-consumer —— M1 验收冒烟的调试会话 SSE 流解析器（stdin → 断言 → 摘要）。

独立成文件的原因：bash 中 `curl | python3 - <<EOF` 的 heredoc 会抢占 stdin，
管道数据丢失；解析器必须以文件形式运行，stdin 才能留给事件流。

用法：curl -N ... | sse-consumer.py [期望回复包含的子串]
退出码：断言失败（SESSION_ERROR / 缺收尾事件 / 空回复 / 期望子串未命中）非零。
"""
import json
import sys

expect = sys.argv[1] if len(sys.argv) > 1 else ""
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
if errors:
    print("❌ SSE 断言失败：" + "；".join(errors), file=sys.stderr)
    sys.exit(1)
