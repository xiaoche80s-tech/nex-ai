package com.gkht.ai.nexai.module.ai.session.application.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;

/**
 * 调试会话分页查询参数（MVP 会话列表按规格过滤）。
 */
@Schema(description = "管理后台 - 调试会话分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionPageQuery extends PageParam {

    @Schema(description = "规格编号（可空）", example = "1")
    private Long specId;

    @Schema(description = "会话状态（READY/ACTIVE/ASKING/CLOSED，可空）", example = "ASKING")
    private String status;

}
