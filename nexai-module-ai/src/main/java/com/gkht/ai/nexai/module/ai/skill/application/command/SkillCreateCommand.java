package com.gkht.ai.nexai.module.ai.skill.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 技能创建命令（携带首个草稿，name/description 从 SKILL.md 解析）")
@Data
public class SkillCreateCommand implements SkillDraftCommand {

    @Schema(description = "SKILL.md 全文（YAML front matter 的 name/description 必填）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "---\nname: pdf-report\ndescription: 生成 PDF 汇报文档\n---\n按模板生成汇报文档。")
    @NotBlank(message = "SKILL.md 内容不能为空")
    @Size(max = 131_072, message = "SKILL.md 内容不能超过 131072 个字符")
    private String skillMd;

    @Schema(description = "附属资源文件集（相对路径 → 文件内容）", example = "{\"scripts/run.py\": \"print('hi')\"}")
    @Size(max = 32, message = "附属资源文件不能超过 32 个")
    private Map<String, String> resources;

}
