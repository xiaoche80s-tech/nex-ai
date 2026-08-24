package com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpConnectionConfig;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP client 建连工厂（SDK 适配唯一处，工单 21 候选 6）：消费 domain 连接配置
 * {@link McpConnectionConfig} 组装 agentscope {@code McpClientBuilder}（三传输 + 认证头，
 * ADR-0001 零自建）。探测网关（mcpserver 内）与运行时挂载（session 侧）共用本工厂——
 * 替代原 {@code AgentscopeMcpServerGateway.buildClient} 静态直调，跨聚合不再有
 * agentscope 类型在 adapter 间裸奔。
 */
@Component
public class McpClientFactory {

    /** 按连接配置建立同步 client（未 initialize——调用方按需 initialize + listTools/enableTools） */
    public McpClientWrapper buildSync(McpConnectionConfig config) {
        McpClientBuilder builder = McpClientBuilder.create(config.clientName());
        Map<String, String> headers = config.headers() == null ? Map.of() : config.headers();
        switch (config.transport()) {
            case STDIO -> builder.stdioTransport(config.command(), config.args(), config.env());
            case SSE -> builder.sseTransport(config.endpoint());
            case STREAMABLE_HTTP -> builder.streamableHttpTransport(config.endpoint());
        }
        if (config.transport() != com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpTransport.STDIO) {
            headers.forEach(builder::header);
        }
        if (config.requestTimeout() != null) {
            builder.timeout(config.requestTimeout());
        }
        return builder.buildSync();
    }
}
