package com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.mcpserver.domain.gateway.McpServerGateway;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpProbeResult;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpToolSummary;
import com.gkht.ai.nexai.module.ai.shared.util.RootCauses;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * MCP Server 探测网关（agentscope 适配器，ADR-0001 直用）：经聚合翻译的连接配置 +
 * {@link McpClientFactory} 建连（与运行时装配同一套建连路径），initialize + listTools
 * 拉取工具清单。探测不抛异常——成败与根因一律封装在 {@link McpProbeResult} 中。
 */
@Component
public class AgentscopeMcpServerGateway implements McpServerGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeMcpServerGateway.class);

    /** 探测整体超时上限（网络异常或服务挂死时及时止损；server 自配超时更小时以其为准） */
    static final Duration PROBE_TIMEOUT_CAP = Duration.ofSeconds(20);
    /** 失败信息截断长度 */
    private static final int MAX_MESSAGE_LENGTH = 500;

    @Resource
    private McpClientFactory mcpClientFactory;

    @Override
    public McpProbeResult probe(McpServer server, Long tenantId) {
        long startNanos = System.nanoTime();
        McpClientWrapper client = null;
        try {
            client = mcpClientFactory.buildSync(server.toConnectionConfig(probeTimeout(server)));
            // listTools() 的 initialized 守卫在链组装期求值，而置位在 initialize() 订阅后——
            // 必须用 Mono.defer 延迟组装（与 agentscope McpClientManager 的链法一致），否则
            // initialize 成功后仍报 "not initialized"
            List<McpSchema.Tool> tools = client.initialize()
                    .then(Mono.defer(client::listTools))
                    .block(PROBE_TIMEOUT_CAP);
            List<McpToolSummary> summaries = tools == null ? List.of() : tools.stream()
                    .map(tool -> McpToolSummary.of(tool.name(), tool.description()))
                    .toList();
            return McpProbeResult.success(elapsedMs(startNanos), summaries.size(), summaries);
        } catch (Exception ex) {
            log.warn("MCP Server 探测失败：tenantId={}, serverId={}, name={}", tenantId,
                    server.getId(), server.getName(), ex);
            return McpProbeResult.failure(elapsedMs(startNanos),
                    RootCauses.rootMessage(ex, MAX_MESSAGE_LENGTH));
        } finally {
            closeQuietly(client);
        }
    }

    /** 探测超时：server 自配超时与探测上限取小 */
    private static Duration probeTimeout(McpServer server) {
        return RootCauses.minTimeout(server.getTimeoutSeconds(), PROBE_TIMEOUT_CAP);
    }

    private static void closeQuietly(McpClientWrapper client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ex) {
                log.debug("关闭探测 MCP client 失败（忽略）：{}", ex.getMessage());
            }
        }
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

}
