package com.gkht.ai.nexai.module.ai.session.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 调试会话工具确认回应命令（HITL 三态）")
@Data
public class DebugSessionConfirmCommand {

    @Schema(description = "确认决定列表，逐条对应确认请求事件中的工具调用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "确认决定列表不能为空")
    @Valid
    private List<Decision> decisions;

    @Schema(description = "单条工具调用的确认决定")
    @Data
    public static class Decision {

        @Schema(description = "待确认工具调用标识（取自 REQUIRE_USER_CONFIRM 事件）", requiredMode = Schema.RequiredMode.REQUIRED, example = "fake-call-0")
        @NotBlank(message = "工具调用标识不能为空")
        private String toolCallId;

        @Schema(description = "工具名", requiredMode = Schema.RequiredMode.REQUIRED, example = "echo")
        @NotBlank(message = "工具名不能为空")
        private String toolName;

        @Schema(description = "工具调用参数 JSON（修改参数后批准时携带改后完整参数；拒绝时可只带原参数）", example = "{\"text\":\"敲黑板\"}")
        @Size(max = 8192, message = "工具参数不能超过 8192 个字符")
        private String arguments;

        @Schema(description = "是否批准执行（false 为拒绝）", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        private boolean approved;
    }

}
