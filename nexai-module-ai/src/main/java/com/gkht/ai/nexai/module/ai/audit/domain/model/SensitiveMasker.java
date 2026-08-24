package com.gkht.ai.nexai.module.ai.audit.domain.model;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计脱敏器（domain 纯函数，工单 14）：工具入参与结果的摘要化规则——
 * 敏感键（password/secret/token/key/authorization 等）的值打码 + 超长截断。
 * 规则默认值可配（保留期限与脱敏规则，spec 决策：默认值可配，先采集不计价）。
 *
 * <p>纯文本实现（不解析 JSON）：入参摘要可能是任意形态的 JSON 片段或纯文本，
 * 按键值模式 {@code "key" : "value"} 正则打码，兼容单双引号与裸键。</p>
 */
public final class SensitiveMasker {

    /** 敏感键名单（小写匹配，含子串） */
    static final Set<String> SENSITIVE_KEY_KEYWORDS = Set.of(
            "password", "passwd", "secret", "token", "apikey", "api_key",
            "authorization", "auth", "credential", "privatekey", "private_key");

    /** 键值对模式："key" : "value" / key: value（带引号值到引号闭合；裸值到逗号/右花括号） */
    private static final Pattern KEY_VALUE_PATTERN =
            Pattern.compile("([\"']?)([A-Za-z_][A-Za-z0-9_.-]*)\\1\\s*:\\s*(\"[^\"]*\"|'[^']*'|[^,}]+)");

    private SensitiveMasker() {
    }

    /**
     * 摘要化（内置敏感键）：敏感键打码 + 截断到 maxLength
     *
     * @param payload   原始载荷（工具入参 JSON / 结果文本），可空
     * @param maxLength 截断长度（&gt; 0），超出部分截断并追加省略标记
     * @return 脱敏摘要；null 入参返回 null
     */
    public static String mask(String payload, int maxLength) {
        return mask(payload, maxLength, null);
    }

    /**
     * 摘要化（敏感键名单可配，工单 14：脱敏规则默认值可配）
     *
     * @param payload     原始载荷，可空
     * @param maxLength   截断长度（&gt; 0）
     * @param extraKeywords 追加敏感键关键词（小写子串匹配），null/空 = 仅内置名单
     * @return 脱敏摘要；null 入参返回 null
     */
    public static String mask(String payload, int maxLength, java.util.Collection<String> extraKeywords) {
        if (payload == null) {
            return null;
        }
        Set<String> effective = SENSITIVE_KEY_KEYWORDS;
        if (extraKeywords != null && !extraKeywords.isEmpty()) {
            effective = new java.util.HashSet<>(SENSITIVE_KEY_KEYWORDS);
            extraKeywords.stream()
                    .filter(keyword -> keyword != null && !keyword.isBlank())
                    .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                    .forEach(effective::add);
        }
        String masked = maskSensitiveValues(payload, effective);
        if (maxLength <= 0) {
            return masked;
        }
        return masked.length() <= maxLength ? masked
                : masked.substring(0, maxLength) + "…(截断)";
    }

    /** 敏感键的值打码（形如 "api_key": "sk-xx" → "api_key": "***"） */
    private static String maskSensitiveValues(String payload, Set<String> sensitiveKeywords) {
        Matcher matcher = KEY_VALUE_PATTERN.matcher(payload);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String key = matcher.group(2).toLowerCase(Locale.ROOT);
            if (!isSensitiveKey(key, sensitiveKeywords)) {
                continue;
            }
            result.append(payload, last, matcher.start(3)).append("\"***\"");
            last = matcher.end(3);
        }
        result.append(payload.substring(last));
        return result.toString();
    }

    /** 键名含敏感关键词即视为敏感（子串匹配，如 my_api_token） */
    static boolean isSensitiveKey(String lowerCasedKey, Set<String> sensitiveKeywords) {
        return sensitiveKeywords.stream().anyMatch(lowerCasedKey::contains);
    }

}
