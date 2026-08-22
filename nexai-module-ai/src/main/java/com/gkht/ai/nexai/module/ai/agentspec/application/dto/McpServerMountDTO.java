package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - MCP 服务挂载（服务 + 工具白名单，M2 预留）")
@Data
public class McpServerMountDTO {

    @Schema(description = "MCP 服务编号", example = "1")
    private Long serverId;

    @Schema(description = "工具白名单（工具名），空 = 该服务全部工具", example = "[\"search\"]")
    private List<String> allowedTools;

}
