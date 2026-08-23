package com.gkht.ai.nexai.module.ai.skill.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Skill 资产 DTO（列表与详情共用；能力包内容随版本详情另查）。
 */
@Schema(description = "管理后台 - Skill 资产 DTO")
@Data
public class SkillDTO {

    @Schema(description = "skill 编号", example = "1")
    private Long id;

    @Schema(description = "技能名称（agent 引用标识，创建后不可变）", example = "data-clean")
    private String name;

    @Schema(description = "描述", example = "数据清洗能力包")
    private String description;

    @Schema(description = "归属层级（TENANT/USER）", example = "TENANT")
    private String ownerLevel;

    @Schema(description = "归属用户编号（用户级），非用户级为 null", example = "1")
    private Long ownerUserId;

    @Schema(description = "当前生效版本号（运行时物化采用），null = 尚无版本", example = "3")
    private Integer currentVersionNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
