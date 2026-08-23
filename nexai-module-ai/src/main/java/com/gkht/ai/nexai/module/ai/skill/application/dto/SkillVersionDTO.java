package com.gkht.ai.nexai.module.ai.skill.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Skill 版本 DTO（列表用，不含全量能力包内容；current 当前版本标识由应用服务设置）。
 */
@Schema(description = "管理后台 - Skill 版本 DTO")
@Data
public class SkillVersionDTO {

    @Schema(description = "版本编号", example = "1")
    private Long id;

    @Schema(description = "版本号（skill 内严格递增）", example = "3")
    private Integer versionNo;

    @Schema(description = "版本备注", example = "补充资源文件")
    private String note;

    @Schema(description = "是否为当前生效版本", example = "true")
    private Boolean current;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
