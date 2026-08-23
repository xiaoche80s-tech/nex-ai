package com.gkht.ai.nexai.module.ai.channel.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道更新命令。apiKey 为 null 表示保留原密钥（编辑界面不回传明文）。
 */
@Schema(description = "管理后台 - 渠道更新命令")
@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelUpdateCommand extends ChannelCreateCommand {

    @Schema(description = "渠道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "渠道编号不能为空")
    private Long id;

}
