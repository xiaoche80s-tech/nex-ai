package com.gkht.ai.nexai.module.ai.mcpserver.domain.gateway;

import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpProbeResult;

/**
 * MCP Server 连通探测端口：按注册的传输与认证配置发起一次真实连接，
 * 成功时拉取工具清单（initialize + listTools）。实现位于 infrastructure
 * （agentscope McpClientBuilder 三传输直用，ADR-0001 零自建）。
 *
 * <p>探测不抛异常——成败与原因一律封装在 {@link McpProbeResult} 中。</p>
 */
public interface McpServerGateway {

    /**
     * @param server   MCP Server 聚合根（携带传输/端点或命令/认证头）
     * @param tenantId 租户编号（日志定位用）
     * @return 探测结果（成败/耗时/说明/工具清单）
     */
    McpProbeResult probe(McpServer server, Long tenantId);

}
