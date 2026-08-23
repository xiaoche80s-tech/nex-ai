package com.gkht.ai.nexai.module.ai.channel.application.query;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道分页查询。
 */
@Schema(description = "管理后台 - 渠道分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelPageQuery extends PageParam {

    @Schema(description = "渠道名称（模糊匹配）", example = "OpenAI")
    private String name;

    @Schema(description = "提供商类型编码", example = "openai")
    private String provider;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

}
