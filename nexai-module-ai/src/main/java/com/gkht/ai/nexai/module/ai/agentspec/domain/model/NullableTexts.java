package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

/**
 * 可空文本规范化（domain/model 包内值对象共用）：空白归 null，其余去首尾空白。
 * 原 normalizeNullable 在 AgentSpec / AgentSpecConfig / AgentSpecVersion 三处逐字复制，
 * 收敛于此。
 */
final class NullableTexts {

    private NullableTexts() {
    }

    static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
