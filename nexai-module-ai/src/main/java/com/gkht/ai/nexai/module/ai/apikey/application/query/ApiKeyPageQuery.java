package com.gkht.ai.nexai.module.ai.apikey.application.query;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 - 租户 API Key 分页查询参数。
 */
@Schema(description = "管理后台 - 租户 API Key 分页查询参数")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiKeyPageQuery extends PageParam {

    @Schema(description = "Key 名称（模糊匹配）", example = "集成")
    private String name;

}
