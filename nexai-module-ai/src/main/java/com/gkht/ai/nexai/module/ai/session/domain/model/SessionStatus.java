package com.gkht.ai.nexai.module.ai.session.domain.model;

/**
 * 会话状态：READY（就绪）/ ACTIVE（运行中）/ ASKING（HITL 挂起等审批）/ CLOSED（已结束）。
 * ASKING 挂起中的审批不丢——恢复会话时若状态为 ASKING，前端重新渲染审批卡片续接。
 */
public enum SessionStatus {
    /** 就绪（可发起消息） */
    READY,
    /** 运行中（有进行中的消息轮次） */
    ACTIVE,
    /** HITL 挂起（敏感工具调用等待人工审批） */
    ASKING,
    /** 已结束 */
    CLOSED
}
