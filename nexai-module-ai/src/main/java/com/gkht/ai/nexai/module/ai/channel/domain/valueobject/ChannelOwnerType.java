package com.gkht.ai.nexai.module.ai.channel.domain.valueobject;

/**
 * 渠道归属维度：MVP 实现租户侧（BYOK），平台共享渠道（平台托管、全租户可用）仅预留字段，
 * 运营入口后置开放。
 */
public enum ChannelOwnerType {

    /** 平台级：平台托管渠道，全租户可用（预留，MVP 不开放） */
    PLATFORM("platform"),

    /** 租户级：租户自带密钥（BYOK），仅本租户可用 */
    TENANT("tenant");

    /** 落库编码 */
    private final String code;

    ChannelOwnerType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 按落库编码解析，未知编码返回 null（由调用方决定报错语义）
     */
    public static ChannelOwnerType of(String code) {
        for (ChannelOwnerType ownerType : values()) {
            if (ownerType.code.equals(code)) {
                return ownerType;
            }
        }
        return null;
    }

}
