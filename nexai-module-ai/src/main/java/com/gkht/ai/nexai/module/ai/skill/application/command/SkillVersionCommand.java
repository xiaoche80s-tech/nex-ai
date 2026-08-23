package com.gkht.ai.nexai.module.ai.skill.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * Skill 版本登记命令：编辑既有 skill 产生新版本（版本链只增不改）。
 */
@Schema(description = "管理后台 - Skill 版本登记命令")
@Data
public class SkillVersionCommand {

    @Schema(description = "skill 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "skill 编号不能为空")
    private Long skillId;

    @Schema(description = "能力包 Markdown（含 YAML frontmatter）", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 65536, message = "能力包 Markdown 不能超过 65536 个字符")
    private String markdown;

    @Schema(description = "资源文件 Map（相对路径 → 内容）")
    private Map<String, String> resources;

    @Schema(description = "版本备注", example = "补充清洗规则")
    @Size(max = 255, message = "版本备注不能超过 255 个字符")
    private String note;

}
