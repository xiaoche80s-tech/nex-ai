package com.gkht.ai.nexai.module.ai.skill.application.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;

/**
 * Skill 分页查询参数（按名称模糊过滤）。
 */
@Schema(description = "管理后台 - Skill 分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillPageQuery extends PageParam {

    @Schema(description = "技能名称（模糊匹配，可空）", example = "data")
    private String name;

}
