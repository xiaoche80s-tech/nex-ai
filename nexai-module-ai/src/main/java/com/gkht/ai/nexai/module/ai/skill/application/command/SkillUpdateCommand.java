package com.gkht.ai.nexai.module.ai.skill.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 技能编辑命令（覆盖草稿；发布后再编辑即生成新草稿）")
@Data
public class SkillUpdateCommand implements SkillDraftCommand {

    @Schema(description = "技能编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "技能编号不能为空")
    private Long id;

    @Schema(description = "SKILL.md 全文（YAML front matter 的 name/description 必填）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "SKILL.md 内容不能为空")
    @Size(max = 131_072, message = "SKILL.md 内容不能超过 131072 个字符")
    private String skillMd;

    @Schema(description = "附属资源文件集（相对路径 → 文件内容）")
    @Size(max = 32, message = "附属资源文件不能超过 32 个")
    private Map<String, String> resources;

}
