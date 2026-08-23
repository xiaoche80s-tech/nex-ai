package com.gkht.ai.nexai.module.ai.session.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 调试会话消息命令：向会话发送一条用户消息（SSE 流式响应的请求体）。
 */
@Schema(description = "管理后台 - 调试会话消息命令")
@Data
public class DebugSessionMessageCommand {

    @Schema(description = "消息内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "帮我写个 hello world")
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4096, message = "消息内容不能超过 4096 个字符")
    private String content;

}
