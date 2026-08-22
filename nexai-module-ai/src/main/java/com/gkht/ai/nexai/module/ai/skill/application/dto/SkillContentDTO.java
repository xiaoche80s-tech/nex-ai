package com.gkht.ai.nexai.module.ai.skill.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 技能内容 DTO（SKILL.md 全文 + 附属资源文件集）")
@Data
public class SkillContentDTO {

    @Schema(description = "SKILL.md 全文（YAML front matter + 正文）")
    private String skillMd;

    @Schema(description = "附属资源文件集（相对路径 → 文件内容）", example = "{\"scripts/run.py\": \"print('hi')\"}")
    private Map<String, String> resources;

}
