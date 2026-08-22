package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 执行环境配置（workspace / 沙箱 / 执行能力）")
@Data
public class ExecutionEnvDTO {

    @Schema(description = "是否启用 workspace（文件工具与落盘总开关）", example = "true")
    private Boolean workspaceEnabled;

    @Schema(description = "是否启用 Docker 沙箱（仅 workspace 启用时可选）", example = "false")
    private Boolean sandboxEnabled;

    @Schema(description = "沙箱内开放的执行能力（SHELL/PYTHON/NODE，仅沙箱模式可选）", example = "[\"PYTHON\"]")
    private List<String> capabilities;

}
