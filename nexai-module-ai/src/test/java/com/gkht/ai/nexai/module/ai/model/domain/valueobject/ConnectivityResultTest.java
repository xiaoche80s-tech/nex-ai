package com.gkht.ai.nexai.module.ai.model.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连通性探测结果值对象测试（S3 接缝）。
 */
public class ConnectivityResultTest {

    @Test
    @DisplayName("成功/失败工厂：字段与语义正确")
    public void factories() {
        ConnectivityResult ok = ConnectivityResult.success(120L, "连通正常");
        ConnectivityResult fail = ConnectivityResult.failure(3_000L, "401 Unauthorized");

        assertTrue(ok.success());
        assertEquals(120L, ok.durationMs());
        assertEquals("连通正常", ok.message());
        assertFalse(fail.success());
        assertEquals(3_000L, fail.durationMs());
        assertEquals("401 Unauthorized", fail.message());
    }

    @Test
    @DisplayName("值对象按值判等")
    public void equalityByValue() {
        assertEquals(ConnectivityResult.success(120L, "连通正常"), ConnectivityResult.success(120L, "连通正常"));
        assertEquals(ConnectivityResult.success(120L, "连通正常").hashCode(),
                ConnectivityResult.success(120L, "连通正常").hashCode());
    }

}
