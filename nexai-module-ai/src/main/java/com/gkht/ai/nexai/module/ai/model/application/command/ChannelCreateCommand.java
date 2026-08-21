package com.gkht.ai.nexai.module.ai.model.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 渠道创建命令")
@Data
public class ChannelCreateCommand {

    @Schema(description = "渠道名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "公司采购的 OpenAI 主渠道")
    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 64, message = "渠道名称不能超过 64 个字符")
    private String name;

    @Schema(description = "提供商类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "openai")
    @NotBlank(message = "提供商类型不能为空")
    @Size(max = 32, message = "提供商类型不能超过 32 个字符")
    private String provider;

    @Schema(description = "端点地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://api.openai.com/v1")
    @NotBlank(message = "端点地址不能为空")
    @Pattern(regexp = "^https?://\\S+$", message = "端点地址必须是 http(s):// 开头的合法 URL")
    @Size(max = 512, message = "端点地址不能超过 512 个字符")
    private String baseUrl;

    @Schema(description = "API 密钥，经 AES 加密落库；ollama 等本地服务可空", example = "sk-1234567890abcdef")
    @Size(max = 512, message = "API 密钥不能超过 512 个字符")
    private String apiKey;

}
