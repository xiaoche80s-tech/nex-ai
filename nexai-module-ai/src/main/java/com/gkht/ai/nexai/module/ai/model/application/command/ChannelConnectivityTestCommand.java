package com.gkht.ai.nexai.module.ai.model.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 渠道连通性测试命令（凭据不落库，直接探测）")
@Data
public class ChannelConnectivityTestCommand {

    @Schema(description = "提供商类型编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "openai")
    @NotBlank(message = "提供商类型不能为空")
    @Size(max = 32, message = "提供商类型不能超过 32 个字符")
    private String provider;

    @Schema(description = "端点地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://api.openai.com/v1")
    @NotBlank(message = "端点地址不能为空")
    @Pattern(regexp = "^https?://\\S+$", message = "端点地址必须是 http(s):// 开头的合法 URL")
    @Size(max = 512, message = "端点地址不能超过 512 个字符")
    private String baseUrl;

    @Schema(description = "API 密钥；编辑已存渠道留空时自动回退其密钥", example = "sk-1234567890abcdef")
    @Size(max = 512, message = "API 密钥不能超过 512 个字符")
    private String apiKey;

    @Schema(description = "编辑已存渠道时传其编号（密钥留空回退用），新建不传", example = "1")
    private Long channelId;

    @Schema(description = "用于探测的模型标识（真实调用必须携带）", requiredMode = Schema.RequiredMode.REQUIRED, example = "gpt-4o")
    @NotBlank(message = "探测模型标识不能为空")
    @Size(max = 128, message = "探测模型标识不能超过 128 个字符")
    private String modelId;

}
