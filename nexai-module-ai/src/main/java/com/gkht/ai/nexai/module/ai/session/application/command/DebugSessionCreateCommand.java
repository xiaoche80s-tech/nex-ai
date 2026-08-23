package com.gkht.ai.nexai.module.ai.session.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 调试会话创建命令：发起一条调试会话（绑定规格，可指定版本号，null = 当前版本）。
 * 会话业务键由服务端生成（dbg-{uuid}），前端不传。
 */
@Schema(description = "管理后台 - 调试会话创建命令")
@Data
public class DebugSessionCreateCommand {

    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "渠道接入调试")
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 128, message = "会话标题不能超过 128 个字符")
    private String title;

    @Schema(description = "规格编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "会话必须绑定规格")
    private Long specId;

    @Schema(description = "版本号（null = 当前版本）", example = "2")
    private Integer versionNo;

}
