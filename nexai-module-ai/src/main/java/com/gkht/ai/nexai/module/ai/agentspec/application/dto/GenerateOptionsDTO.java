package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 模型调用参数（与 agentscope GenerateOptions 同名同义）")
@Data
public class GenerateOptionsDTO {

    @Schema(description = "采样温度（0 ~ 2），null 表示运行时取默认", example = "0.7")
    private Double temperature;

    @Schema(description = "核采样阈值（0 ~ 1），null 表示运行时取默认", example = "0.9")
    private Double topP;

    @Schema(description = "单次生成的最大 tokens，null 表示运行时取默认", example = "4096")
    private Integer maxTokens;

}
