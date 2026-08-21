package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 智能体规格默认版本切换命令")
@Data
public class AgentSpecSwitchVersionCommand {

    @Schema(description = "规格编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "规格编号不能为空")
    private Long id;

    @Schema(description = "目标版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号不能小于 1")
    @Max(value = 9999, message = "版本号超出合理范围")
    private Integer versionNo;

}
