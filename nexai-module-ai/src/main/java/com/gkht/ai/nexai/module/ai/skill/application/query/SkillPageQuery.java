package com.gkht.ai.nexai.module.ai.skill.application.query;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 技能分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillPageQuery extends PageParam {

    @Schema(description = "技能名，模糊匹配", example = "pdf")
    private String name;

}
