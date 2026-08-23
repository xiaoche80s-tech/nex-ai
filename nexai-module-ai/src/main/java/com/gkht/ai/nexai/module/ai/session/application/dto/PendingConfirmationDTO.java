package com.gkht.ai.nexai.module.ai.session.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * HITL 挂起上下文 DTO：会话处于 ASKING 状态时，前端据此渲染审批卡片并回传三态决定。
 * 由 RequireUserConfirmEvent 的 ToolUseBlock 列表翻译而来（工具名 + 参数摘要 + 权限上下文）。
 */
@Schema(description = "管理后台 - HITL 挂起上下文 DTO")
@Data
public class PendingConfirmationDTO {

    @Schema(description = "工具调用 ID（回传审批决定时原样携带）", example = "call-1")
    private String toolCallId;

    @Schema(description = "工具名", example = "write_file")
    private String toolName;

    @Schema(description = "工具参数 JSON（摘要展示）", example = "{\"path\":\"a.txt\"}")
    private String argumentsJson;

}
