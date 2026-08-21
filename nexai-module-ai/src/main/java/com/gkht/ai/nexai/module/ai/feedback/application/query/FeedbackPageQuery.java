package com.gkht.ai.nexai.module.ai.feedback.application.query;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 问题反馈分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackPageQuery extends PageParam {

    @Schema(description = "处理状态编码（10 待处理 / 20 处理中 / 30 已解决 / 40 已关闭）", example = "10")
    private Integer status;

    @Schema(description = "反馈内容模糊匹配", example = "乱码")
    private String content;

}
