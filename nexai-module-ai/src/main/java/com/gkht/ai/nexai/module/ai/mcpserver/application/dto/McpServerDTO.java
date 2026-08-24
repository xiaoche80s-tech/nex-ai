package com.gkht.ai.nexai.module.ai.mcpserver.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server DTO。认证头与 STDIO 环境变量（可能含凭证）不出参。
 */
@Schema(description = "管理后台 - MCP Server DTO")
@Data
public class McpServerDTO {

    @Schema(description = "MCP Server 编号", example = "1")
    private Long id;

    @Schema(description = "名称", example = "文件工具服务")
    private String name;

    @Schema(description = "传输类型", example = "STREAMABLE_HTTP")
    private String transport;

    @Schema(description = "端点地址（stdio 传输为 null）", example = "http://mcp.example.com/mcp")
    private String endpoint;

    @Schema(description = "启动命令（HTTP 系传输为 null）", example = "npx")
    private String command;

    @Schema(description = "STDIO 启动参数")
    private List<String> args;

    @Schema(description = "是否已配置认证头（HTTP 系传输）", example = "true")
    private Boolean headersConfigured;

    @Schema(description = "请求超时（秒），null = 运行时默认", example = "30")
    private Integer timeoutSeconds;

    @Schema(description = "工具白名单，空 = 该 Server 全部工具", example = "[\"read_file\"]")
    private List<String> allowedTools;

    @Schema(description = "最近探测拉取的工具名清单（信息性缓存）")
    private List<String> availableTools;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "归属维度", example = "tenant")
    private String ownerType;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
