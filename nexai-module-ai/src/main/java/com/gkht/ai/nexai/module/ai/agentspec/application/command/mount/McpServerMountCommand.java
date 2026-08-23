package com.gkht.ai.nexai.module.ai.agentspec.application.command.mount;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * MCP 服务挂载命令（挂载层统一模式「引用 + 可选工具白名单」，编辑面后置）。
 */
@Schema(description = "管理后台 - MCP 服务挂载命令（服务 + 工具白名单）")
@Data
public class McpServerMountCommand {

    @Schema(description = "MCP Server 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "MCP 挂载必须携带服务编号")
    private Long serverId;

    @Schema(description = "工具白名单（工具名），空 = 该服务全部工具", example = "[\"search\"]")
    @Size(max = 128, message = "工具白名单不能超过 128 项")
    private List<String> allowedTools;

}
