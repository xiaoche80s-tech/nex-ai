package com.gkht.ai.nexai.module.ai.model.domain.valueobject;

/**
 * API 密钥脱敏规则：保留前 4 后 4 位，不足 8 位全遮蔽。
 * 聚合展示与 DO 直查转 DTO 共用，保证全平台脱敏口径一致。
 */
public final class ApiKeyMasker {

    private ApiKeyMasker() {
    }

    /**
     * 脱敏密钥；未配置（null/空串）返回 null
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

}
