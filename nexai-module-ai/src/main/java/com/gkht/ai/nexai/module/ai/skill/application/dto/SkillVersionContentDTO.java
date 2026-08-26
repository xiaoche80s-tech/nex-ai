package com.gkht.ai.nexai.module.ai.skill.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 版本内容 DTO（version-get 预览用）：SKILL.md 全文 + 资源相对路径清单。
 * 资源文件内容较大，按需加载，此处仅透出路径。
 */
@Schema(description = "管理后台 - Skill 版本内容 DTO")
@Data
public class SkillVersionContentDTO {

    @Schema(description = "所属 skill 编号", example = "1")
    private Long skillId;

    @Schema(description = "版本号", example = "2")
    private Integer versionNo;

    @Schema(description = "版本备注", example = "补充规则文件")
    private String note;

    @Schema(description = "SKILL.md 全文（YAML frontmatter + 正文）")
    private String markdown;

    @Schema(description = "资源文件相对路径清单")
    private List<String> resourcePaths;

    @Schema(description = "版本创建时间")
    private LocalDateTime createTime;

}
