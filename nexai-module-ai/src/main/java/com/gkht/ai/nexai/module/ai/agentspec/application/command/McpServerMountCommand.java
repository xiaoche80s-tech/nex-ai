package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * MCP 服务挂载命令：服务引用 + 可选工具白名单（空 = 该服务全部工具）。
 */
@Schema(description = "管理后台 - MCP 服务挂载（服务 + 工具白名单）")
@Data
public class McpServerMountCommand {

    @Schema(description = "MCP 服务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "MCP 服务挂载必须指定服务")
    private Long serverId;

    @Schema(description = "工具白名单（工具名），不填 = 该服务全部工具", example = "[\"search\"]")
    private List<String> allowedTools;

}
