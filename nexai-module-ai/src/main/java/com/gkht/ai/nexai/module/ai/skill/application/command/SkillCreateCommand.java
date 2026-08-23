package com.gkht.ai.nexai.module.ai.skill.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * Skill 创建命令：创建 skill 资产并携带首个版本（能力包 Markdown + 资源文件）。
 */
@Schema(description = "管理后台 - Skill 创建命令")
@Data
public class SkillCreateCommand {

    @Schema(description = "技能名称（创建后不可变，agent 引用标识）", requiredMode = Schema.RequiredMode.REQUIRED, example = "data-clean")
    @NotBlank(message = "技能名称不能为空")
    @Size(max = 64, message = "技能名称不能超过 64 个字符")
    private String name;

    @Schema(description = "描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "数据清洗能力包")
    @NotBlank(message = "技能描述不能为空")
    @Size(max = 512, message = "技能描述不能超过 512 个字符")
    private String description;

    @Schema(description = "归属层级（TENANT 默认 / USER）", example = "TENANT")
    private String ownerLevel;

    @Schema(description = "能力包 Markdown（含 YAML frontmatter：name/description 必填）", requiredMode = Schema.RequiredMode.REQUIRED, example = "---\nname: data-clean\ndescription: 数据清洗\n---\n# 数据清洗\n\n清洗规则...")
    @NotBlank(message = "能力包 Markdown 不能为空")
    @Size(max = 65536, message = "能力包 Markdown 不能超过 65536 个字符")
    private String markdown;

    @Schema(description = "资源文件 Map（相对路径 → 内容；base64: 前缀表达二进制）")
    private Map<String, String> resources;

    @Schema(description = "版本备注", example = "首版")
    @Size(max = 255, message = "版本备注不能超过 255 个字符")
    private String note;

}
