package com.gkht.ai.nexai.module.ai.channel.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 渠道连通性探测命令：不落库直接探测（表单即测）；channelId 携带且密钥留空时回退已存密钥。
 */
@Schema(description = "管理后台 - 渠道连通性探测命令（表单即测，不落库）")
@Data
public class ChannelConnectivityTestCommand {

    @Schema(description = "渠道编号（编辑已存渠道且密钥留空时回退其已存密钥），表单新建探测为空", example = "1")
    private Long channelId;

    @Schema(description = "提供商类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "openai")
    @NotBlank(message = "提供商类型不能为空")
    private String provider;

    @Schema(description = "端点地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://api.openai.com/v1")
    @NotBlank(message = "端点地址不能为空")
    private String baseUrl;

    @Schema(description = "API 密钥（留空且携带 channelId 时回退已存密钥）", example = "sk-xxx")
    @Size(max = 1024, message = "API 密钥不能超过 1024 个字符")
    private String apiKey;

    @Schema(description = "被探测的模型标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "gpt-4o")
    @NotBlank(message = "模型标识不能为空")
    @Size(max = 128, message = "模型标识不能超过 128 个字符")
    private String modelId;

}
