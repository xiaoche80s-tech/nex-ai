package com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject;

/**
 * MCP 传输类型（agentscope McpClientBuilder 三传输直用）。
 */
public enum McpTransport {

    /** 标准输入输出子进程（command + args + env） */
    STDIO,

    /** HTTP SSE 长连接 */
    SSE,

    /** Streamable HTTP */
    STREAMABLE_HTTP

}
