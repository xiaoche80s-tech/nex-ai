package com.gkht.ai.nexai.module.ai.agentspec.application.command.mount;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 工具挂载命令（挂载层统一模式「工具来源 + 引用 + 可选白名单」，编辑面后置）。
 * MCP 与平台工具库（@Tool 业务工具）共用本结构；内置工具随执行环境层启用，不走挂载。
 */
@Schema(description = "管理后台 - 工具挂载命令（来源 + 引用 + 工具白名单）")
@Data
public class ToolMountCommand {

    @Schema(description = "工具来源（MCP = MCP Server，PLATFORM = 平台工具库）", requiredMode = Schema.RequiredMode.REQUIRED, example = "MCP")
    @NotNull(message = "工具挂载必须声明来源")
    @Pattern(regexp = "MCP|PLATFORM", message = "工具来源仅支持 MCP（MCP Server）与 PLATFORM（平台工具库）")
    private String source;

    @Schema(description = "来源条目编号（MCP = MCP Server 编号；PLATFORM = 平台工具库条目编号）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "工具挂载必须携带来源条目编号")
    private Long sourceId;

    @Schema(description = "工具白名单（工具名），空 = 该来源全部工具", example = "[\"search\"]")
    @Size(max = 128, message = "工具白名单不能超过 128 项")
    private List<String> allowedTools;

}
