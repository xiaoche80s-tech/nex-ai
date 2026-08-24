package com.gkht.ai.nexai.module.ai.mcpserver.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 更新命令。headers 传 null 或空 Map 表示保留原认证头（编辑界面不回传凭证）。
 */
@Schema(description = "管理后台 - MCP Server 更新命令")
@Data
public class McpServerUpdateCommand {

    @Schema(description = "MCP Server 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "文件工具服务")
    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "名称不能超过 64 个字符")
    private String name;

    @Schema(description = "传输类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "SSE")
    @NotBlank(message = "传输类型不能为空")
    @Pattern(regexp = "STDIO|SSE|STREAMABLE_HTTP", message = "传输类型仅支持 STDIO/SSE/STREAMABLE_HTTP")
    private String transport;

    @Schema(description = "端点地址（SSE/STREAMABLE_HTTP 必填，http/https）", example = "http://mcp.example.com/sse")
    @Size(max = 512, message = "端点地址不能超过 512 个字符")
    private String endpoint;

    @Schema(description = "启动命令（STDIO 必填）", example = "npx")
    @Size(max = 255, message = "启动命令不能超过 255 个字符")
    private String command;

    @Schema(description = "STDIO 启动参数")
    private List<String> args;

    @Schema(description = "STDIO 环境变量（凭证走此处）")
    private Map<String, String> env;

    @Schema(description = "认证头（null = 保留原值；STDIO 不支持）")
    private Map<String, String> headers;

    @Schema(description = "请求超时（秒），不填运行时取默认", example = "30")
    private Integer timeoutSeconds;

    @Schema(description = "工具白名单（工具名），空 = 该 Server 全部工具", example = "[\"read_file\"]")
    @Size(max = 128, message = "工具白名单不能超过 128 项")
    private List<String> allowedTools;

}
