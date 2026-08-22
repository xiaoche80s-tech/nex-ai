package com.gkht.ai.nexai.module.ai.session.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 调试会话克隆命令：复制源会话的对话历史为新调试会话，可微调推理参数重跑")
@Data
public class DebugSessionCloneCommand {

    @Schema(description = "新会话标题，缺省取「源标题（克隆）」", example = "客服智能体联调·高温对照")
    private String title;

    @Schema(description = "最大迭代轮数覆盖，null 沿用源会话当前值（其本身 null 则取版本快照）", example = "5")
    private Integer maxIters;

    @Schema(description = "温度覆盖，null 沿用源会话当前值", example = "0.9")
    private Double temperature;

}
