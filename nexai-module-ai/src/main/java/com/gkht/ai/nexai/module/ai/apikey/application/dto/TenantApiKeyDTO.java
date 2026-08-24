package com.gkht.ai.nexai.module.ai.apikey.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台 - 租户 API Key 列表项 DTO（不含明文/哈希——密文不回传）。
 */
@Schema(description = "管理后台 - 租户 API Key 列表项")
@Data
public class TenantApiKeyDTO {

    @Schema(description = "Key 编号")
    private Long id;

    @Schema(description = "Key 名称")
    private String name;

    @Schema(description = "识别前缀（展示面，如 nexai-abc123…）")
    private String keyPrefix;

    @Schema(description = "状态（ENABLED/REVOKED）")
    private String status;

    @Schema(description = "规格范围（specCode 白名单，空 = 本租户全部规格）")
    private List<String> specCodes;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
