package com.gkht.ai.nexai.module.ai.session.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * HITL 审批命令：对挂起中的敏感工具调用做三态审批（确认/拒绝/改参数）。
 * decisions 与 RequireUserConfirmEvent 推送的工具调用一一对应。
 */
@Schema(description = "管理后台 - HITL 审批命令")
@Data
public class DebugSessionConfirmCommand {

    @Schema(description = "审批决定列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "审批决定不能为空")
    private List<Decision> decisions;

    /** 单个工具调用的三态决定 */
    @Schema(description = "工具调用审批决定")
    @Data
    public static class Decision {

        @Schema(description = "工具调用 ID（RequireUserConfirmEvent 推送）", requiredMode = Schema.RequiredMode.REQUIRED, example = "call-1")
        @NotBlank(message = "工具调用 ID 不能为空")
        private String toolCallId;

        @Schema(description = "工具名", requiredMode = Schema.RequiredMode.REQUIRED, example = "write_file")
        @NotBlank(message = "工具名不能为空")
        private String toolName;

        @Schema(description = "是否确认（true = 确认/改参数，false = 拒绝）", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        @NotNull(message = "审批结果不能为空")
        private Boolean approved;

        @Schema(description = "工具参数 JSON（确认不改参数时可不传；改参数时传修改后参数）", example = "{\"path\":\"a.txt\"}")
        @Size(max = 8192, message = "工具参数不能超过 8192 个字符")
        private String argumentsJson;
    }

}
