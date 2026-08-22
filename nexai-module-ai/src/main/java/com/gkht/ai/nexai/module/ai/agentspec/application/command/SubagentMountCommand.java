package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 子智能体挂载命令：规格引用 + 可选工具白名单（空 = 继承父智能体全部工具）。
 */
@Schema(description = "管理后台 - 子智能体挂载（规格 + 工具白名单）")
@Data
public class SubagentMountCommand {

    @Schema(description = "被挂载的规格编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "子智能体挂载必须指定规格")
    private Long specId;

    @Schema(description = "工具白名单（工具名），不填 = 继承父智能体全部工具", example = "[\"search\"]")
    private List<String> tools;

}
