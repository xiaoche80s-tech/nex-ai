package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.Objects;

/**
 * 模型调用参数值对象（不可变，按值判等）：与 agentscope 的 GenerateOptions 同名同义，
 * 运行时装配时直接映射为 agentscope 的 GenerateOptions（temperature 等属模型调用层，
 * 不在 agent 顶层——这是对齐 agentscope 分层的关键）。
 *
 * <p>全部字段可空，null 表示运行时取提供商默认；整个值对象也可为 null（配置组不填）。</p>
 */
public final class GenerateOptions {

    /** 温度上限（业界提供商普遍接受 0 ~ 2） */
    static final double TEMPERATURE_MAX = 2.0d;
    /** topP 上限 */
    static final double TOP_P_MAX = 1.0d;

    /** 采样温度（0 ~ 2），null = 默认 */
    private final Double temperature;
    /** 核采样概率阈值（0 ~ 1），null = 默认 */
    private final Double topP;
    /** 单次生成的最大 tokens（>= 1），null = 默认 */
    private final Integer maxTokens;

    private GenerateOptions(Double temperature, Double topP, Integer maxTokens) {
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
    }

    /**
     * 构建调用参数
     *
     * @param temperature 温度，可空，须在 [0, 2] 内
     * @param topP        核采样阈值，可空，须在 [0, 1] 内
     * @param maxTokens   最大 tokens，可空，须 >= 1
     */
    public static GenerateOptions of(Double temperature, Double topP, Integer maxTokens) {
        if (temperature != null && (temperature < 0d || temperature > TEMPERATURE_MAX)) {
            throw new IllegalArgumentException("温度必须在 0 ~ " + TEMPERATURE_MAX + " 之间");
        }
        if (topP != null && (topP < 0d || topP > TOP_P_MAX)) {
            throw new IllegalArgumentException("topP 必须在 0 ~ " + TOP_P_MAX + " 之间");
        }
        if (maxTokens != null && maxTokens < 1) {
            throw new IllegalArgumentException("最大 tokens 不能小于 1");
        }
        return new GenerateOptions(temperature, topP, maxTokens);
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GenerateOptions other)) {
            return false;
        }
        return Objects.equals(temperature, other.temperature)
                && Objects.equals(topP, other.topP)
                && Objects.equals(maxTokens, other.maxTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(temperature, topP, maxTokens);
    }

}
