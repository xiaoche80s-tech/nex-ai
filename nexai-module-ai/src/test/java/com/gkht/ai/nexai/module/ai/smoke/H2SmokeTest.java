package com.gkht.ai.nexai.module.ai.smoke;

import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 内存库基类冒烟：验证 BaseDbUnitTest 上下文在本模块可启动、数据源可用。
 */
public class H2SmokeTest extends BaseDbUnitTest {

    @Resource
    private DataSource dataSource;

    @Test
    public void testH2DataSourceAvailable() throws Exception {
        try (var conn = dataSource.getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}
