package com.gkht.ai.nexai.module.ai.channel.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型更新命令（全量替换，可改挂渠道）。
 */
@Schema(description = "管理后台 - 模型更新命令")
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelUpdateCommand extends ModelCreateCommand {

    @Schema(description = "模型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "模型编号不能为空")
    private Long id;

}
