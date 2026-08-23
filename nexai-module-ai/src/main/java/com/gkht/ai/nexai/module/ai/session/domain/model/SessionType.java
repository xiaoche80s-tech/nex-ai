package com.gkht.ai.nexai.module.ai.session.domain.model;

/**
 * 会话类型：调试（DEBUG，租户管理员调试台）与终端（CHAT，终端用户聊天页）。
 * 类型决定发起入口与状态恢复语义（MVP 调试先行）。
 */
public enum SessionType {
    /** 调试会话（调试台） */
    DEBUG,
    /** 终端会话（终端聊天页） */
    CHAT
}
