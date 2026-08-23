package com.gkht.ai.nexai.module.ai.channel.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 渠道创建命令。归属维度 MVP 固定租户侧（BYOK），不由前端传入；
 * 提供商编码合法性由服务层解析并转友好错误码（CHANNEL_PROVIDER_INVALID）。
 */
@Schema(description = "管理后台 - 渠道创建命令（BYOK）")
@Data
public class ChannelCreateCommand {

    @Schema(description = "渠道名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "公司采购的 OpenAI 主渠道")
    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 64, message = "渠道名称不能超过 64 个字符")
    private String name;

    @Schema(description = "提供商类型编码（openai/openai-compat/dashscope/anthropic/gemini/ollama）", requiredMode = Schema.RequiredMode.REQUIRED, example = "openai")
    @NotBlank(message = "提供商类型不能为空")
    private String provider;

    @Schema(description = "端点地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://api.openai.com/v1")
    @NotBlank(message = "端点地址不能为空")
    @Size(max = 512, message = "端点地址不能超过 512 个字符")
    private String baseUrl;

    @Schema(description = "API 密钥（本地服务可空）", example = "sk-xxx")
    @Size(max = 1024, message = "API 密钥不能超过 1024 个字符")
    private String apiKey;

}
