package com.gkht.ai.nexai.module.ai.skill.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 技能发布命令")
@Data
public class SkillPublishCommand {

    @Schema(description = "技能编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "技能编号不能为空")
    private Long id;

    @Schema(description = "发布说明", example = "补充资源脚本")
    @Size(max = 255, message = "发布说明不能超过 255 个字符")
    private String remark;

}
