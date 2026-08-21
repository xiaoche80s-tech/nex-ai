package com.gkht.ai.nexai.module.ai.model.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 渠道启停命令")
@Data
public class ChannelUpdateStatusCommand {

    @Schema(description = "渠道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "渠道编号不能为空")
    private Long id;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

}
