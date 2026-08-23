package com.gkht.ai.nexai.module.ai.channel.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道 DTO。密钥一律脱敏出参，明文不出应用层。
 */
@Schema(description = "管理后台 - 渠道 DTO")
@Data
public class ChannelDTO {

    @Schema(description = "渠道编号", example = "1")
    private Long id;

    @Schema(description = "渠道名称", example = "公司采购的 OpenAI 主渠道")
    private String name;

    @Schema(description = "提供商类型编码", example = "openai")
    private String provider;

    @Schema(description = "端点地址", example = "https://api.openai.com/v1")
    private String baseUrl;

    @Schema(description = "API 密钥脱敏展示（前 4 后 4），未配置为 null", example = "sk-1****cdef")
    private String apiKeyMasked;

    @Schema(description = "是否已配置密钥（本地服务可未配置）", example = "true")
    private Boolean apiKeyConfigured;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "归属维度（platform 平台共享 / tenant 租户自有）", example = "tenant")
    private String ownerType;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
