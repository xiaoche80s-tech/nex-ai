#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""fake-openai-server —— M1 验收冒烟用的 OpenAI 兼容 mock 模型服务。

仅依赖 Python 标准库。实现 /v1/chat/completions 的流式（SSE）子集，足够
agentscope OpenAIChatModel（stream=true + stream_options.include_usage）完成真实
HTTP 调用链：后端会真的发 HTTP 请求、真的解析 SSE 增量、真的归集 token 用量。

回复是无状态的——跨请求记忆恢复由被测系统（PostgresAgentStateStore 把完整历史
重新传给模型）负责，因此本服务从收到的全部 messages 里找答案即可验证记忆链路：

- 用户消息含「我叫X」       → 回复「好的，我记住了：你叫X。」
- 用户消息问「我叫什么名字」 → 扫描全部历史找「我叫X」，命中回「你叫X」，未命中明示不知
- 用户消息含「长回复」       → 分 12 片慢速输出（每片间隔约 0.3s），供中断演示
- 其他                      → 回显收到的消息轮数与前 20 字

用法：python3 fake-openai-server.py [端口]（默认 18080）
"""
import json
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

NAME_RE = re.compile(r"我叫([\u4e00-\u9fa5A-Za-z0-9]{1,10})")


def _text_of(message):
    """兼容字符串与多段 content 两种 OpenAI 消息形态，取出纯文本。"""
    content = message.get("content") or ""
    if isinstance(content, list):
        content = " ".join(
            str(part.get("text", "")) for part in content if isinstance(part, dict)
        )
    return str(content)


def build_reply(messages):
    last_user = next((m for m in reversed(messages) if m.get("role") == "user"), None)
    if last_user is None:
        return "（fake-openai-server：未收到用户消息）"
    content = _text_of(last_user)
    if "长回复" in content:
        return "长回复演示：" + "这是一段用于演示中断功能的较长回复文本。" * 12
    if "我叫什么" in content:
        for m in messages:
            hit = NAME_RE.search(_text_of(m))
            if hit:
                return "你叫%s。（跨请求记忆恢复验证：名字提取自你此前轮次的消息，说明完整历史已被重新送入模型）" % hit.group(1)
        return "你还没有告诉过我你的名字。"
    hit = NAME_RE.search(content)
    if hit:
        return "好的，我记住了：你叫%s。" % hit.group(1)
    return "（fake-openai-server 收到 %d 轮历史）你说：%s" % (len(messages), content[:20])


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[fake-openai] %s\n" % (fmt % args))

    def _json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _sse_chunk(self, model, delta=None, finish_reason=None, usage=None):
        chunk = {
            "id": "chatcmpl-fake",
            "object": "chat.completion.chunk",
            "created": int(time.time()),
            "model": model,
            "choices": [
                {"index": 0, "delta": {} if delta is None else {"content": delta},
                 "finish_reason": finish_reason}
            ],
        }
        if usage is not None:
            chunk["usage"] = usage
        self.wfile.write(
            ("data: %s\n\n" % json.dumps(chunk, ensure_ascii=False)).encode("utf-8")
        )

    def do_POST(self):
        path = self.path.split("?")[0]
        if path.rstrip("/") not in ("/v1/chat/completions", "/chat/completions"):
            self._json(404, {"error": {"message": "not found: %s" % path}})
            return
        try:
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        except (ValueError, TypeError):
            self._json(400, {"error": {"message": "invalid json"}})
            return
        messages = body.get("messages", [])
        model = body.get("model", "fake-model")
        reply = build_reply(messages)
        prompt_tokens = sum(len(_text_of(m)) for m in messages)

        slow = "长回复" in (_text_of(messages[-1]) if messages else "")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        try:
            for i in range(0, len(reply), 6):
                if slow:
                    time.sleep(0.3)
                self._sse_chunk(model, delta=reply[i:i + 6])
            self._sse_chunk(
                model,
                finish_reason="stop",
                usage={
                    "prompt_tokens": prompt_tokens,
                    "completion_tokens": len(reply),
                    "total_tokens": prompt_tokens + len(reply),
                },
            )
            self.wfile.write(b"data: [DONE]\n\n")
        except (BrokenPipeError, ConnectionResetError):
            pass  # 客户端中断（interrupt 演示）属预期


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print("fake-openai-server listening on 127.0.0.1:%d" % port, file=sys.stderr)
    server.serve_forever()
