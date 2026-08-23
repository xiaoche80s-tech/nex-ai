package com.gkht.ai.nexai.module.ai.channel.application.query;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型分页查询。
 */
@Schema(description = "管理后台 - 模型分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelPageQuery extends PageParam {

    @Schema(description = "所属渠道编号", example = "1")
    private Long channelId;

    @Schema(description = "模型标识（模糊匹配）", example = "gpt")
    private String modelId;

    @Schema(description = "显示名（模糊匹配）", example = "GPT")
    private String name;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

}
