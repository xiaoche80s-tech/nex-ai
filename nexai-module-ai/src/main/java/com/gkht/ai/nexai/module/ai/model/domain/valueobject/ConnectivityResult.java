package com.gkht.ai.nexai.module.ai.model.domain.valueobject;

import java.util.Objects;

/**
 * 连通性探测结果值对象：成败、耗时与说明信息，不可变、按值判等。
 */
public record ConnectivityResult(boolean success, long durationMs, String message) {

    /**
     * 探测成功
     */
    public static ConnectivityResult success(long durationMs, String message) {
        return new ConnectivityResult(true, durationMs, message);
    }

    /**
     * 探测失败（携带失败原因）
     */
    public static ConnectivityResult failure(long durationMs, String message) {
        return new ConnectivityResult(false, durationMs, message);
    }

}
