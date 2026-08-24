package com.gkht.ai.nexai.module.ai.mcpserver.domain.model;

import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpOwnerType;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Server 聚合根领域测试（纯 JUnit 直构实体）：三传输条件必填/互斥校验、
 * 认证头保留语义、白名单子集校验、探测结果登记、启停。
 */
public class McpServerTest {

    private static final McpOwnerType TENANT = McpOwnerType.TENANT;

    @Test
    @DisplayName("注册 Streamable HTTP Server：端点必填，命令互斥为空")
    public void createStreamableHttpServer() {
        McpServer server = McpServer.create("文件工具", McpTransport.STREAMABLE_HTTP,
                "http://mcp.example.com/mcp", null, null, null,
                Map.of("Authorization", "Bearer token"), 30, List.of("read_file"), TENANT);
        assertNull(server.getId());
        assertTrue(server.isEnabled());
        assertEquals(McpTransport.STREAMABLE_HTTP, server.getTransport());
        assertEquals("http://mcp.example.com/mcp", server.getEndpoint());
        assertNull(server.getCommand());
        assertEquals(Map.of("Authorization", "Bearer token"), server.getHeaders());
        assertEquals(List.of("read_file"), server.getAllowedTools());
        assertTrue(server.getAvailableTools().isEmpty(), "新建时无探测缓存");
    }

    @Test
    @DisplayName("注册 stdio Server：命令必填，端点/认证头互斥拒绝")
    public void createStdioServer() {
        McpServer server = McpServer.create("本地工具", McpTransport.STDIO, null,
                "npx", List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"),
                Map.of("API_KEY", "secret"), null, null, null, TENANT);
        assertEquals("npx", server.getCommand());
        assertEquals(Map.of("API_KEY", "secret"), server.getEnv());

        // 端点与认证头对 stdio 无意义：显式拒绝而非静默忽略
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x", McpTransport.STDIO,
                "http://mcp.local", "npx", null, null, null, null, null, TENANT));
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x", McpTransport.STDIO,
                null, "npx", null, null, Map.of("Authorization", "Bearer t"), null, null, TENANT));
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x", McpTransport.STDIO,
                null, null, null, null, null, null, null, TENANT));
    }

    @Test
    @DisplayName("注册 HTTP 系 Server：端点必须 http/https，缺失或非法拒绝")
    public void createHttpServerRequiresValidEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x",
                McpTransport.SSE, null, null, null, null, null, null, null, TENANT));
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x",
                McpTransport.SSE, "ftp://mcp.local/sse", null, null, null, null, null, null, TENANT));
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x",
                McpTransport.STREAMABLE_HTTP, "http://mcp.local/mcp", "npx", null, null,
                null, null, null, TENANT), "HTTP 传输不使用启动命令");
    }

    @Test
    @DisplayName("更新：headers 传 null 保留原认证头（凭证只能保留或更换）")
    public void updateKeepsHeadersWhenNull() {
        McpServer server = McpServer.create("x", McpTransport.SSE, "http://mcp.local/sse",
                null, null, null, Map.of("Authorization", "Bearer old"), null, null, TENANT);
        server.update("y", McpTransport.SSE, "http://mcp.local/sse2", null, null, null,
                null, 60, null);
        assertEquals("y", server.getName());
        assertEquals("http://mcp.local/sse2", server.getEndpoint());
        assertEquals(Map.of("Authorization", "Bearer old"), server.getHeaders(), "null 保留原认证头");
        assertEquals(60, server.getTimeoutSeconds());
    }

    @Test
    @DisplayName("更新：白名单必须在最近拉取的工具清单内（防拼写错）")
    public void updateWhitelistMustBeSubsetOfAvailable() {
        McpServer server = McpServer.create("x", McpTransport.SSE, "http://mcp.local/sse",
                null, null, null, null, null, null, TENANT);
        server.applyProbeResult(List.of("read_file", "write_file"));

        // 无探测缓存时不做子集校验（首配白名单先于首次探测是合法时序）
        McpServer fresh = McpServer.create("y", McpTransport.SSE, "http://mcp.local/sse",
                null, null, null, null, null, List.of("anything"), TENANT);

        // 有缓存后白名单须为子集
        server.update("x", McpTransport.SSE, "http://mcp.local/sse", null, null, null,
                null, null, List.of("read_file"));
        assertThrows(IllegalArgumentException.class, () -> server.update("x", McpTransport.SSE,
                "http://mcp.local/sse", null, null, null, null, null,
                List.of("read_file", "typo_tool")));
        assertEquals("y", fresh.getName());
    }

    @Test
    @DisplayName("探测结果登记：成功刷新工具清单缓存；空清单/超限拒绝")
    public void applyProbeResultValidates() {
        McpServer server = McpServer.create("x", McpTransport.SSE, "http://mcp.local/sse",
                null, null, null, null, null, null, TENANT);
        assertThrows(IllegalArgumentException.class, () -> server.applyProbeResult(List.of()));
        server.applyProbeResult(List.of("read_file", "write_file"));
        assertEquals(List.of("read_file", "write_file"), server.getAvailableTools());
    }

    @Test
    @DisplayName("启停：停用保留数据与凭证，仅退出来用范围")
    public void enableDisable() {
        McpServer server = McpServer.create("x", McpTransport.SSE, "http://mcp.local/sse",
                null, null, null, Map.of("Authorization", "Bearer t"), null, null, TENANT);
        server.disable();
        assertFalse(server.isEnabled());
        assertEquals(Map.of("Authorization", "Bearer t"), server.getHeaders());
        server.enable();
        assertTrue(server.isEnabled());
    }

    @Test
    @DisplayName("边界校验：名称/超时/白名单规模")
    public void boundaryValidations() {
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("",
                McpTransport.SSE, "http://mcp.local/sse", null, null, null, null, null, null, TENANT));
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x",
                McpTransport.SSE, "http://mcp.local/sse", null, null, null, null, 0, null, TENANT));
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x",
                McpTransport.SSE, "http://mcp.local/sse", null, null, null, null, null,
                List.of(" "), TENANT));
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x",
                null, "http://mcp.local/sse", null, null, null, null, null, null, TENANT));
        assertThrows(IllegalArgumentException.class, () -> McpServer.create("x",
                McpTransport.SSE, "http://mcp.local/sse", null, null, null, null, null, null, null));
    }

}
