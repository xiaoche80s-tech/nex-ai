package com.gkht.ai.nexai.module.ai.channel.domain.valueobject;

/**
 * 渠道提供商类型值对象。
 *
 * <p>同一提供商可有多个渠道（如多个 openai-compat 中转），渠道差异体现在端点与密钥上。
 * 取值与 agentscope 模型扩展一一对应（ChatModelFactory 的构造分支）。</p>
 */
public enum ChannelProvider {

    /** OpenAI 官方 */
    OPENAI("openai"),
    /** OpenAI 兼容端点（各类中转/自建网关） */
    OPENAI_COMPAT("openai-compat"),
    /** 阿里云通义千问 */
    DASHSCOPE("dashscope"),
    /** Anthropic Claude */
    ANTHROPIC("anthropic"),
    /** Google Gemini */
    GEMINI("gemini"),
    /** Ollama 本地服务（通常无需密钥） */
    OLLAMA("ollama");

    /** 落库编码，同时作为前端字典值 */
    private final String code;

    ChannelProvider(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 按落库编码解析，未知编码返回 null（由调用方决定报错语义）
     */
    public static ChannelProvider of(String code) {
        for (ChannelProvider provider : values()) {
            if (provider.code.equals(code)) {
                return provider;
            }
        }
        return null;
    }

}
