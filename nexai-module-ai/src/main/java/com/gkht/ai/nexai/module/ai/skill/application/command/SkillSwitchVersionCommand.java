package com.gkht.ai.nexai.module.ai.skill.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 技能切换默认版本命令")
@Data
public class SkillSwitchVersionCommand {

    @Schema(description = "技能编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "技能编号不能为空")
    private Long id;

    @Schema(description = "目标版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "目标版本号不能为空")
    @Min(value = 1, message = "版本号不能小于 1")
    @Max(value = 999_999, message = "版本号过大")
    private Integer versionNo;

}
