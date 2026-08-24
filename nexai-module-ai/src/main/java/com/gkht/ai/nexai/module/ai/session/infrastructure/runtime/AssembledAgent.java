package com.gkht.ai.nexai.module.ai.session.infrastructure.runtime;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 装配结果（gateway 装配翻译的产出）：agent 实例 + 需随实例善后关闭的 MCP client 清单。
 * 关闭职责内聚于此——框架不级联关闭 MCP client，由装配结果一并善后（工单 13）。
 */
public record AssembledAgent(HarnessAgent agent, List<McpClientWrapper> mcpClients) {

    private static final Logger log = LoggerFactory.getLogger(AssembledAgent.class);

    /** 善后关闭：agent 后台资源（转录镜像排空/TaskRepository/workspace 索引）+ MCP client */
    public void closeQuietly() {
        try {
            agent.close();
        } catch (Exception ex) {
            log.warn("关闭常驻实例失败：{}", agent.getName(), ex);
        }
        mcpClients.forEach(AssembledAgent::closeClientQuietly);
    }

    /** 单个 MCP client 安静关闭（装配失败降级路径与善后共用） */
    public static void closeClientQuietly(McpClientWrapper client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ex) {
                log.debug("关闭 MCP client 失败（忽略）：{}", ex.getMessage());
            }
        }
    }
}
