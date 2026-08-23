package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 智能体规格切换当前版本命令：仅回退当前版本指针，快照本身不可变。
 */
@Schema(description = "管理后台 - 智能体规格切换当前版本命令")
@Data
public class AgentSpecSwitchVersionCommand {

    @Schema(description = "规格编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "规格编号不能为空")
    private Long id;

    @Schema(description = "目标版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号必须为正整数")
    private Integer versionNo;

}
