package com.gkht.ai.nexai.module.ai.agentspec.application.query;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 智能体规格分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSpecPageQuery extends PageParam {

    @Schema(description = "规格名称（模糊匹配）", example = "客服")
    private String name;

}
