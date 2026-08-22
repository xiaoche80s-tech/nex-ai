package com.gkht.ai.nexai.module.ai.session.domain.exception;

/**
 * 会话已有运行中的事件流时再次发起（发消息 / 确认回应）抛出。
 * 调试会话同一时刻只允许一条事件流在跑：中断（停止按钮）或等待流终结后再发。
 */
public class SessionRunningException extends RuntimeException {

    public SessionRunningException(String sessionKey) {
        super("会话 " + sessionKey + " 已有运行中的事件流，请先停止或等待其结束");
    }

}
