package com.gkht.ai.nexai.module.ai.mcpserver.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * MCP Server 启停命令。
 */
@Schema(description = "管理后台 - MCP Server 启停命令")
@Data
public class McpServerUpdateStatusCommand {

    @Schema(description = "MCP Server 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "编号不能为空")
    private Long id;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

}
