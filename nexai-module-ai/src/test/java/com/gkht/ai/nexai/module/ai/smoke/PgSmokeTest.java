package com.gkht.ai.nexai.module.ai.smoke;

import com.gkht.ai.nexai.module.ai.support.BasePgDbAndRedisUnitTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PG 集成基类冒烟：验证 BasePgDbAndRedisUnitTest 直连真实 PostgreSQL 可用（连接信息
 * application-pg-test.yaml，环境变量 NEXAI_TEST_PG_URL/USERNAME/PASSWORD 可覆盖）。
 */
public class PgSmokeTest extends BasePgDbAndRedisUnitTest {

    @Resource
    private DataSource dataSource;

    @Test
    public void testPostgresReachable() throws Exception {
        try (var conn = dataSource.getConnection()) {
            assertEquals("PostgreSQL", conn.getMetaData().getDatabaseProductName());
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
