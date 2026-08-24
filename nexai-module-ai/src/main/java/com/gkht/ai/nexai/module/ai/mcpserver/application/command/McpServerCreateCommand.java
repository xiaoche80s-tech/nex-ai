package com.gkht.ai.nexai.module.ai.mcpserver.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 创建命令：传输相关的端点/命令/认证按传输类型条件必填（领域层校验互斥关系）。
 */
@Schema(description = "管理后台 - MCP Server 创建命令")
@Data
public class McpServerCreateCommand {

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "文件工具服务")
    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "名称不能超过 64 个字符")
    private String name;

    @Schema(description = "传输类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "STREAMABLE_HTTP")
    @NotBlank(message = "传输类型不能为空")
    @Pattern(regexp = "STDIO|SSE|STREAMABLE_HTTP", message = "传输类型仅支持 STDIO/SSE/STREAMABLE_HTTP")
    private String transport;

    @Schema(description = "端点地址（SSE/STREAMABLE_HTTP 必填，http/https）", example = "http://mcp.example.com/mcp")
    @Size(max = 512, message = "端点地址不能超过 512 个字符")
    private String endpoint;

    @Schema(description = "启动命令（STDIO 必填）", example = "npx")
    @Size(max = 255, message = "启动命令不能超过 255 个字符")
    private String command;

    @Schema(description = "STDIO 启动参数", example = "[\"-y\",\"@modelcontextprotocol/server-filesystem\",\"/tmp\"]")
    private List<String> args;

    @Schema(description = "STDIO 环境变量（凭证走此处）")
    private Map<String, String> env;

    @Schema(description = "认证头（仅 HTTP 系传输，如 Authorization；STDIO 不支持）", example = "{\"Authorization\":\"Bearer xxx\"}")
    private Map<String, String> headers;

    @Schema(description = "请求超时（秒），不填运行时取默认", example = "30")
    private Integer timeoutSeconds;

    @Schema(description = "工具白名单（工具名），空 = 该 Server 全部工具", example = "[\"read_file\"]")
    @Size(max = 128, message = "工具白名单不能超过 128 项")
    private List<String> allowedTools;

}
