package com.gkht.ai.nexai.module.ai.mcpserver.application.query;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Server 分页查询。
 */
@Schema(description = "管理后台 - MCP Server 分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class McpServerPageQuery extends PageParam {

    @Schema(description = "名称（模糊匹配）", example = "文件")
    private String name;

    @Schema(description = "传输类型（精确）", example = "SSE")
    private String transport;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

}
