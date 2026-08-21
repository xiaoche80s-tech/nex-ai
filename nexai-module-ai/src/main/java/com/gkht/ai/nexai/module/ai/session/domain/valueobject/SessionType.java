package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

/**
 * 会话类型值对象。调试会话与终端用户会话同属 Session 聚合，以类型区分治理口径：
 * debug 面向智能体开发者（调试台，可绑定任意已发布版本）；portal 面向终端用户（M2，仅可绑定默认版本）。
 */
public enum SessionType {

    /** 调试会话：调试台发起 */
    DEBUG(10),
    /** 终端用户会话：门户发起（M2 交付，编码先占位） */
    PORTAL(20);

    /** 数据库存储编码（ai_session.type） */
    private final int code;

    SessionType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 按存储编码解析，非法编码抛出 IllegalArgumentException
     */
    public static SessionType of(int code) {
        for (SessionType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的会话类型编码：" + code);
    }
}
