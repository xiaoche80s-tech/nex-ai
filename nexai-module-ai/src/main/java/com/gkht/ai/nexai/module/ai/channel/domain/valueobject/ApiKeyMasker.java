package com.gkht.ai.nexai.module.ai.channel.domain.valueobject;

/**
 * 密钥脱敏展示规则：长度不足 8 位整体打码，否则保留前 4 后 4。
 * 明文密钥只在聚合内与加密列中存在，任何出参一律经此脱敏。
 */
public final class ApiKeyMasker {

    private ApiKeyMasker() {
    }

    /**
     * @return 脱敏后的密钥；入参 null / 空串返回 null
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
