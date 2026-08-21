package com.gkht.ai.nexai.module.ai.model.domain.valueobject;

/**
 * 渠道归属维度值对象：平台共享（M3）或租户自有。
 *
 * <p>M1 只开放租户侧创建；platform 值随表字段先行落地，供工单 24 平台共享渠道使用。</p>
 */
public enum ChannelOwnerType {

    /** 平台共享渠道：平台代充密钥，租户加成计价（M3） */
    PLATFORM("platform"),
    /** 租户自有渠道：租户管理员创建并自带密钥 */
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
