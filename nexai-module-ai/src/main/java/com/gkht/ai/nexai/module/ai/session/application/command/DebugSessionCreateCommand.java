package com.gkht.ai.nexai.module.ai.session.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Schema(description = "管理后台 - 调试会话创建命令：绑定规格的已发布版本")
@Data
public class DebugSessionCreateCommand {

    @Schema(description = "规格编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "规格编号不能为空")
    private Long specId;

    @Schema(description = "规格版本号；缺省绑定规格当前默认版本", example = "1")
    @Positive(message = "规格版本号必须为正整数")
    private Integer versionNo;

    @Schema(description = "会话标题，缺省由前端以规格名补齐", example = "客服智能体联调")
    @Size(max = 128, message = "会话标题不能超过 128 个字符")
    private String title;

}
