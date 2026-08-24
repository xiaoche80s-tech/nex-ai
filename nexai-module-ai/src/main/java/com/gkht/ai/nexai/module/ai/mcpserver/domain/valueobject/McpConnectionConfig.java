package com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP 连接配置（纯 domain 值对象，工单 21 候选 6）：McpServer 聚合经
 * {@code McpServer.toConnectionConfig} 翻译出的连接参数快照——传输类型、端点或命令、
 * 认证头与请求超时。SDK 侧（agentscope McpClientBuilder）的组装由 mcpserver
 * infrastructure 的建连工厂消费本配置完成；跨聚合经本值对象传递，agentscope 类型
 * 不再在 adapter 间裸奔（原 AgentscopeRuntimeGateway 静态直调 buildClient 的替代）。
 *
 * @param clientName     client 实例名（日志与追踪定位）
 * @param transport      传输类型（STDIO / SSE / STREAMABLE_HTTP）
 * @param command        STDIO 命令（非 STDIO 为 null）
 * @param args           STDIO 命令参数（非 STDIO 为 null）
 * @param env            STDIO 环境变量（非 STDIO 为 null）
 * @param endpoint       远端端点（SSE / STREAMABLE_HTTP，STDIO 为 null）
 * @param headers        认证头（仅远端传输）
 * @param requestTimeout 请求超时，null = SDK 默认
 */
public record McpConnectionConfig(String clientName, McpTransport transport,
                                  String command, List<String> args, Map<String, String> env,
                                  String endpoint, Map<String, String> headers,
                                  Duration requestTimeout) {
}
