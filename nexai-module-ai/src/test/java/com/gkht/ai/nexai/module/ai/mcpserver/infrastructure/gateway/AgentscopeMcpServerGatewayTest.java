package com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.gateway;

import com.gkht.ai.nexai.framework.test.core.ut.BaseMockitoUnitTest;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpOwnerType;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpProbeResult;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpTransport;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 探测网关时序回归测试：agentscope McpSyncClientWrapper 的 initialized 守卫在
 * listTools() 方法调用（链组装）期求值，而置位发生在 initialize() 订阅后——
 * probe 若写成 initialize().then(client.listTools())，listTools 会在订阅前被组装、
 * 拿到 error Mono，探测必败（IllegalStateException: not initialized）。
 * 本测试用复刻该守卫语义的假 client 锁住「先初始化完成、再组装 listTools」的顺序。
 */
public class AgentscopeMcpServerGatewayTest extends BaseMockitoUnitTest {

    @Mock
    private McpClientFactory mcpClientFactory;

    @InjectMocks
    private AgentscopeMcpServerGateway gateway;

    @Test
    public void testProbeListsToolsAfterInitializeCompletes() {
        GuardedFakeClient client = new GuardedFakeClient(List.of(
                new McpSchema.Tool("echo", null, "回声工具", null, null, null, null)));
        when(mcpClientFactory.buildSync(any())).thenReturn(client);

        McpServer server = McpServer.create("demo", McpTransport.STREAMABLE_HTTP,
                "http://127.0.0.1:9999/mcp", null, null, null, null, null, null,
                McpOwnerType.TENANT);

        McpProbeResult result = gateway.probe(server, 1L);

        assertTrue(result.isSuccess(), "探测应成功，实际失败消息：" + result.getMessage());
        assertEquals(1, result.getTools().size());
        assertEquals("echo", result.getTools().get(0).getName());
    }

    /** 复刻 McpSyncClientWrapper 的关键语义：listTools 组装期守卫 + initialize 订阅期置位 */
    static final class GuardedFakeClient extends McpClientWrapper {

        private final List<McpSchema.Tool> tools;

        GuardedFakeClient(List<McpSchema.Tool> tools) {
            super("test-mcp");
            this.tools = tools;
        }

        @Override
        public Mono<Void> initialize() {
            if (initialized) {
                return Mono.empty();
            }
            return Mono.fromRunnable(() -> initialized = true)
                    .subscribeOn(Schedulers.boundedElastic())
                    .then();
        }

        @Override
        public Mono<List<McpSchema.Tool>> listTools() {
            if (!initialized) {
                return Mono.error(new IllegalStateException("MCP client '" + name + "' not initialized"));
            }
            return Mono.fromCallable(() -> tools)
                    .subscribeOn(Schedulers.boundedElastic());
        }

        @Override
        public Mono<McpSchema.CallToolResult> callTool(String toolName, Map<String, Object> arguments) {
            return Mono.error(new UnsupportedOperationException("测试桩不支持 callTool"));
        }

        @Override
        public Mono<McpSchema.CallToolResult> callTool(String toolName, Map<String, Object> arguments,
                                                       Map<String, Object> meta) {
            return Mono.error(new UnsupportedOperationException("测试桩不支持 callTool"));
        }

        @Override
        public void close() {
            initialized = false;
        }
    }
}
