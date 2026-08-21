package com.gkht.ai.nexai.module.ai.session.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 调试会话发消息命令")
@Data
public class DebugSessionMessageCommand {

    @Schema(description = "用户消息内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "你好，请介绍一下你自己")
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4096, message = "消息内容不能超过 4096 个字符")
    private String content;

}
