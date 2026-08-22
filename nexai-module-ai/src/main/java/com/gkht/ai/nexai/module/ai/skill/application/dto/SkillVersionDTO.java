package com.gkht.ai.nexai.module.ai.skill.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 技能版本 DTO（不可变快照）")
@Data
public class SkillVersionDTO {

    @Schema(description = "版本记录编号", example = "1")
    private Long id;

    @Schema(description = "所属技能编号", example = "1")
    private Long skillId;

    @Schema(description = "版本号", example = "1")
    private Integer versionNo;

    @Schema(description = "发布说明", example = "首个版本")
    private String remark;

    @Schema(description = "技能内容快照（SKILL.md 原文 + 附属资源）")
    private SkillContentDTO content;

    @Schema(description = "发布时间")
    private LocalDateTime createTime;

}
