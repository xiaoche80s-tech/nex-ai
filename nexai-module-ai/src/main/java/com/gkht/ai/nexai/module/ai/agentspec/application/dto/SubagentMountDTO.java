package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 子智能体挂载（规格 + 工具白名单，M2 预留）")
@Data
public class SubagentMountDTO {

    @Schema(description = "被挂载的规格编号", example = "2")
    private Long specId;

    @Schema(description = "工具白名单（工具名），空 = 继承父智能体全部工具", example = "[\"search\"]")
    private List<String> tools;

}
