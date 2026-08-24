package com.gkht.ai.nexai.module.ai.mcpserver.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * MCP Server 连通探测结果 DTO。
 */
@Schema(description = "管理后台 - MCP Server 连通探测结果 DTO")
@Data
public class McpProbeResultDTO {

    @Schema(description = "是否成功", example = "true")
    private Boolean success;

    @Schema(description = "耗时（毫秒）", example = "320")
    private Long elapsedMs;

    @Schema(description = "说明（失败时为根因消息）", example = "连通正常，拉取到 3 个工具")
    private String message;

    @Schema(description = "拉取到的工具清单（失败时为空）")
    private List<McpToolDTO> tools;

    /** 工具清单条目 */
    @Data
    public static class McpToolDTO {

        @Schema(description = "工具名", example = "read_file")
        private String name;

        @Schema(description = "工具描述")
        private String description;

    }

}
