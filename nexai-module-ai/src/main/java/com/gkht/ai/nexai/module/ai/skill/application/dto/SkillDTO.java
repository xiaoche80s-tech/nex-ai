package com.gkht.ai.nexai.module.ai.skill.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 技能列表项 DTO")
@Data
public class SkillDTO {

    @Schema(description = "技能编号", example = "1")
    private Long id;

    @Schema(description = "技能名（SKILL.md front matter 的 name，运行时挂载寻址键）", example = "pdf-report")
    private String name;

    @Schema(description = "技能描述（SKILL.md front matter 的 description）", example = "生成 PDF 汇报文档")
    private String description;

    @Schema(description = "已发布的最新版本号，从未发布为 0", example = "1")
    private Integer latestVersionNo;

    @Schema(description = "当前默认版本号（运行时读取的版本），从未发布为 null", example = "1")
    private Integer currentVersionNo;

    @Schema(description = "是否有未发布草稿")
    private Boolean hasDraft;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
