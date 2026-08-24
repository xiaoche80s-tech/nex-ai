package com.gkht.ai.nexai.module.ai.shared.util;

/**
 * 异常根因消息提取（共享工具，channel/mcpserver/session 探测与装配网关共用）：
 * 逐层解包取根因消息，带异常类型名便于分辨鉴权/超时/连接拒绝，并截断到展示上限。
 */
public final class RootCauses {

    private RootCauses() {
    }

    /**
     * @param ex              待解包异常（Reactor 会用 RuntimeException 包装底层 HTTP 异常）
     * @param maxLength       展示截断长度（调用方错误消息可能携带长响应体）
     */
    public static String rootMessage(Throwable ex, int maxLength) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        } else {
            message = current.getClass().getSimpleName() + ": " + message;
        }
        return message.length() > maxLength ? message.substring(0, maxLength) + "…" : message;
    }

    /**
     * 超时取小：自配超时与上限取小（探测/装配连接的网络异常及时止损）
     */
    public static java.time.Duration minTimeout(Integer configuredSeconds,
                                                java.time.Duration cap) {
        return configuredSeconds == null ? cap
                : java.time.Duration.ofSeconds(
                        Math.min(configuredSeconds, cap.toSeconds()));
    }

}
