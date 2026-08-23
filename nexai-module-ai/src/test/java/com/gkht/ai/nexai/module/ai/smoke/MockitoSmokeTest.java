package com.gkht.ai.nexai.module.ai.smoke;

import com.gkht.ai.nexai.framework.test.core.ut.BaseMockitoUnitTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Mockito 基类冒烟：验证 BaseMockitoUnitTest 在本模块可正常驱动 mock 与 verify。
 */
public class MockitoSmokeTest extends BaseMockitoUnitTest {

    @Test
    public void testMockitoAvailable() {
        List<String> list = mock(List.class);
        list.add("nexai");
        verify(list).add("nexai");
    }
}
