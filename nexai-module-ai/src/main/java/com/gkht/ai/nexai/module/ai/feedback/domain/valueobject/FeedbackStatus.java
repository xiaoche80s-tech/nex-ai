package com.gkht.ai.nexai.module.ai.feedback.domain.valueobject;

/**
 * 问题反馈处理状态（值对象）。
 *
 * <p>流转规则：已关闭（CLOSED）为终态，不允许流转出去；其余状态之间允许互转（含退回待处理、
 * 已解决重开），但不允许流转到自身（无意义的空转）。</p>
 */
public enum FeedbackStatus {

    /** 待处理 */
    PENDING(10, "待处理"),
    /** 处理中 */
    PROCESSING(20, "处理中"),
    /** 已解决 */
    RESOLVED(30, "已解决"),
    /** 已关闭（终态） */
    CLOSED(40, "已关闭");

    /** 状态编码，落库与对外契约使用 */
    private final int code;
    /** 展示名 */
    private final String label;

    FeedbackStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按编码解析状态，未知编码或 null 返回 null（由调用方决定如何报错）
     */
    public static FeedbackStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (FeedbackStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断能否流转到目标状态
     */
    public boolean canTransitionTo(FeedbackStatus target) {
        if (this == CLOSED || target == null) {
            return false;
        }
        return this != target;
    }

}
