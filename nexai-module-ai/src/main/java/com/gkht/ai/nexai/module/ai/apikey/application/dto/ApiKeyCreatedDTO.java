package com.gkht.ai.nexai.module.ai.apikey.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 租户 API Key 创建结果 DTO（工单 16）：明文 Key 仅此一次返回
 * （密文存储，之后不可再取）。
 */
@Schema(description = "管理后台 - 租户 API Key 创建结果（明文仅此一次返回）")
@Data
public class ApiKeyCreatedDTO {

    @Schema(description = "Key 编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "明文 Key（仅此一次返回，请妥善保存）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "nexai-abc123...")
    private String apiKey;

    @Schema(description = "识别前缀（列表展示面）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String keyPrefix;

}
