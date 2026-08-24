package com.gkht.ai.nexai.module.ai.apikey.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 租户 API Key 创建命令（工单 16）：明文 Key 由服务端生成并在响应中
 * 一次性返回，不落库（密文存储）。
 */
@Schema(description = "管理后台 - 租户 API Key 创建命令")
@Data
public class ApiKeyCreateCommand {

    @Schema(description = "Key 名称（展示用）", requiredMode = Schema.RequiredMode.REQUIRED, example = "集成专用")
    @NotBlank(message = "Key 名称不能为空")
    @Size(max = 64, message = "Key 名称不能超过 64 个字符")
    private String name;

    @Schema(description = "规格范围（specCode 白名单，空 = 本租户全部规格）", example = "[\"customer-service\"]")
    @Size(max = 128, message = "规格范围不能超过 128 项")
    private List<String> specCodes;

}
