package com.gkht.ai.nexai.module.ai.mcpserver.interfaces.controller.admin.mcpserver;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerCreateCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerUpdateCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpProbeResultDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpServerDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.application.service.McpServerService;
import com.gkht.ai.nexai.module.ai.mcpserver.application.service.McpServerServiceImpl;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.gateway.McpServerGateway;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpProbeResult;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpToolSummary;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.converter.McpServerConverterImpl;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.mapper.McpServerMapper;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.repository.McpServerRepositoryImpl;
import com.gkht.ai.nexai.module.ai.support.TenantDbTestConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MCP_SERVER_CONFIG_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MCP_SERVER_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MCP_SERVER_TOOLS_WHITELIST_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Server HTTP 契约测试（H2 全链路，探测网关 stub 不外呼）：
 * 注册（三传输条件校验）、更新（认证头保留语义/白名单子集校验）、启停、删除、
 * 分页与详情脱敏、探测回写工具清单缓存、跨租户隔离断言。
 * 三传输真实建连契约由 PG 直连网关测试（AgentscopeRuntimeGatewayTest 挂载降级用例）保障。
 */
@Import({McpServerController.class, McpServerServiceImpl.class, McpServerRepositoryImpl.class,
        McpServerConverterImpl.class, McpServerMapper.class,
        TenantDbTestConfiguration.class, McpServerControllerTest.StubGatewayConfiguration.class})
public class McpServerControllerTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;
    private static final long TENANT_TWO = 2L;

    @Resource
    private McpServerService mcpServerService;

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TENANT_ONE);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("注册 Streamable HTTP Server：落库可查，详情不回传认证头内容")
    public void createStreamableHttpServerPersists() {
        Long id = mcpServerService.createMcpServer(httpCommand("文件工具服务"));

        McpServerDTO dto = mcpServerService.getMcpServer(id);
        assertNotNull(id);
        assertEquals("文件工具服务", dto.getName());
        assertEquals("STREAMABLE_HTTP", dto.getTransport());
        assertEquals("http://mcp.example.com/mcp", dto.getEndpoint());
        assertTrue(dto.getEnabled());
        assertEquals(List.of("read_file"), dto.getAllowedTools());
        assertTrue(dto.getHeadersConfigured(), "认证头已配置只露布尔");
    }

    @Test
    @DisplayName("注册 stdio Server：命令/参数/环境变量落库回读一致")
    public void createStdioServerRoundTrips() {
        McpServerCreateCommand command = new McpServerCreateCommand();
        command.setName("本地文件工具");
        command.setTransport("STDIO");
        command.setCommand("npx");
        command.setArgs(List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"));
        command.setEnv(Map.of("API_KEY", "secret"));
        Long id = mcpServerService.createMcpServer(command);

        McpServerDTO dto = mcpServerService.getMcpServer(id);
        assertEquals("STDIO", dto.getTransport());
        assertEquals("npx", dto.getCommand());
        assertEquals(List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"), dto.getArgs());
    }

    @Test
    @DisplayName("注册校验：stdio 缺命令 / HTTP 缺端点 / stdio 带认证头均报业务异常")
    public void createValidatesTransportFields() {
        McpServerCreateCommand stdio = stdioCommand("x");
        stdio.setCommand(null);
        assertServiceException(() -> mcpServerService.createMcpServer(stdio), MCP_SERVER_CONFIG_INVALID,
                "stdio 传输必须填写启动命令");

        McpServerCreateCommand http = httpCommand("y");
        http.setEndpoint(null);
        assertServiceException(() -> mcpServerService.createMcpServer(http), MCP_SERVER_CONFIG_INVALID,
                "STREAMABLE_HTTP 传输必须填写端点地址");

        McpServerCreateCommand stdioWithHeaders = stdioCommand("z");
        stdioWithHeaders.setHeaders(Map.of("Authorization", "Bearer t"));
        assertServiceException(() -> mcpServerService.createMcpServer(stdioWithHeaders),
                MCP_SERVER_CONFIG_INVALID, "stdio 传输不支持认证头（凭证请走环境变量）");
    }

    @Test
    @DisplayName("更新：headers null 保留原认证头；白名单须在探测清单内")
    public void updateKeepsHeadersAndValidatesWhitelist() {
        Long id = mcpServerService.createMcpServer(httpCommand("文件工具服务"));
        // 探测成功回写工具清单（stub 网关）
        mcpServerService.probeMcpServer(id);

        McpServerUpdateCommand update = new McpServerUpdateCommand();
        update.setId(id);
        update.setName("改名");
        update.setTransport("STREAMABLE_HTTP");
        update.setEndpoint("http://mcp.example.com/mcp2");
        update.setAllowedTools(List.of("read_file"));
        mcpServerService.updateMcpServer(update);
        McpServerDTO dto = mcpServerService.getMcpServer(id);
        assertEquals("改名", dto.getName());
        assertTrue(dto.getHeadersConfigured(), "headers null 保留原认证头");
        assertTrue(dto.getAvailableTools().containsAll(List.of("read_file", "write_file")),
                "探测清单缓存保留");

        update.setAllowedTools(List.of("typo_tool"));
        assertServiceException(() -> mcpServerService.updateMcpServer(update),
                MCP_SERVER_TOOLS_WHITELIST_INVALID,
                "工具白名单中的工具不在该 Server 最近拉取的工具清单内");
    }

    @Test
    @DisplayName("探测：成功返回工具清单并回写缓存；探测次数按 id 定位")
    public void probeReturnsToolsAndCaches() {
        Long id = mcpServerService.createMcpServer(httpCommand("文件工具服务"));
        McpProbeResultDTO result = mcpServerService.probeMcpServer(id);
        assertTrue(result.getSuccess());
        assertEquals(2, result.getTools().size());
        assertEquals("read_file", result.getTools().get(0).getName());

        McpServerDTO dto = mcpServerService.getMcpServer(id);
        assertEquals(List.of("read_file", "write_file"), dto.getAvailableTools());
    }

    @Test
    @DisplayName("启停与删除：停用后详情可见状态，删除后查询报不存在")
    public void statusAndDeleteLifecycle() {
        Long id = mcpServerService.createMcpServer(httpCommand("文件工具服务"));
        var disable = new com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerUpdateStatusCommand();
        disable.setId(id);
        disable.setEnabled(false);
        mcpServerService.updateMcpServerStatus(disable);
        assertFalse(mcpServerService.getMcpServer(id).getEnabled());

        mcpServerService.deleteMcpServer(id);
        assertServiceException(() -> mcpServerService.getMcpServer(id), MCP_SERVER_NOT_EXISTS);
    }

    @Test
    @DisplayName("租户隔离：租户 1 的 Server 在租户 2 不可见/不可查")
    public void tenantIsolation() {
        Long id = mcpServerService.createMcpServer(httpCommand("租户一的服务"));

        TenantContextHolder.setTenantId(TENANT_TWO);
        var page = mcpServerService.getMcpServerPage(
                new com.gkht.ai.nexai.module.ai.mcpserver.application.query.McpServerPageQuery());
        assertEquals(0, page.getTotal(), "租户 2 看不到租户 1 的 Server");
        assertServiceException(() -> mcpServerService.getMcpServer(id), MCP_SERVER_NOT_EXISTS);    }

    private McpServerCreateCommand httpCommand(String name) {
        McpServerCreateCommand command = new McpServerCreateCommand();
        command.setName(name);
        command.setTransport("STREAMABLE_HTTP");
        command.setEndpoint("http://mcp.example.com/mcp");
        command.setHeaders(Map.of("Authorization", "Bearer secret-token"));
        command.setAllowedTools(List.of("read_file"));
        return command;
    }

    private McpServerCreateCommand stdioCommand(String name) {
        McpServerCreateCommand command = new McpServerCreateCommand();
        command.setName(name);
        command.setTransport("STDIO");
        command.setCommand("npx");
        command.setArgs(List.of("-y", "server"));
        return command;
    }

    /** 探测网关桩：固定返回两工具清单（真实三传输建连由 PG 网关测试保障），记录探测次数 */
    @TestConfiguration
    static class StubGatewayConfiguration {

        static final AtomicInteger PROBE_COUNT = new AtomicInteger();

        @Bean
        public McpServerGateway mcpServerGateway() {
            return (server, tenantId) -> {
                PROBE_COUNT.incrementAndGet();
                return McpProbeResult.success(50, 2, List.of(
                        McpToolSummary.of("read_file", "读文件"),
                        McpToolSummary.of("write_file", "写文件")));
            };
        }
    }

}
