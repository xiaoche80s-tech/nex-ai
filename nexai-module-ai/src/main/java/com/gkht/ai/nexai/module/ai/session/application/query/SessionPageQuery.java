package com.gkht.ai.nexai.module.ai.session.application.query;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 会话分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionPageQuery extends PageParam {

    @Schema(description = "会话类型编码（10 调试 / 20 终端用户）", example = "10")
    private Integer type;

    @Schema(description = "规格编号（精确匹配）", example = "1")
    private Long specId;

    @Schema(description = "规格版本号（精确匹配，选填规格后可用）", example = "1")
    private Integer versionNo;

}
