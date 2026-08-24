package com.gkht.ai.nexai.module.ai.audit.domain.model;

/**
 * 工具调用结果（审计口径，从 agentscope {@code ToolResultState} 收敛）：
 * 谁-何时-哪个会话-调了什么工具-结果摘要 的「结果」维度。
 */
public enum ToolOutcome {

    /** 工具执行成功 */
    SUCCESS,

    /** 工具执行出错 */
    ERROR,

    /** 工具被拒绝（HITL 审批拒绝或权限拒绝） */
    DENIED,

    /** 执行被中断 */
    INTERRUPTED,

    /** 无结果事件收尾（流终止而未见 ToolResultEnd，防御性口径） */
    UNKNOWN;

    /** agentscope ToolResultState 名 → 审计口径（未知值归 UNKNOWN，不因框架枚举演进丢事件） */
    public static ToolOutcome fromStateName(String stateName) {
        if (stateName == null) {
            return UNKNOWN;
        }
        for (ToolOutcome outcome : values()) {
            if (outcome.name().equals(stateName)) {
                return outcome;
            }
        }
        return UNKNOWN;
    }

}
