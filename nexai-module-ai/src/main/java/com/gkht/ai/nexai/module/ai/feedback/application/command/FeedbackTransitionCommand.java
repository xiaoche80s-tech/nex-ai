package com.gkht.ai.nexai.module.ai.feedback.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 问题反馈状态流转命令")
@Data
public class FeedbackTransitionCommand {

    @Schema(description = "反馈编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "反馈编号不能为空")
    private Long id;

    @Schema(description = "目标处理状态编码（10 待处理 / 20 处理中 / 30 已解决 / 40 已关闭）", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    @NotNull(message = "目标处理状态不能为空")
    private Integer targetStatus;

}
