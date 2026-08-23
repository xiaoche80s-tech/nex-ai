package com.gkht.ai.nexai.module.ai.support;

import cn.hutool.extra.spring.SpringUtil;
import com.gkht.ai.nexai.framework.datasource.config.NexaiDataSourceAutoConfiguration;
import com.gkht.ai.nexai.framework.mybatis.config.NexaiMybatisAutoConfiguration;
import com.gkht.ai.nexai.framework.redis.config.NexaiRedisAutoConfiguration;
import com.gkht.ai.nexai.framework.test.config.RedisTestConfiguration;
import com.gkht.ai.nexai.framework.test.config.SqlInitializationTestConfiguration;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 依赖真实 PostgreSQL + 内存 Redis 的集成测试基类（session 运行时链路专用，AgentRuntimeGateway 接缝测试）。
 *
 * <p>与 {@code BaseDbAndRedisUnitTest}（H2）的区别仅在数据源：会话运行时依赖 PG 专属能力
 * （agentscope PostgresAgentStateStore 状态存储、text 快照列），H2 无法保真——接缝测试
 * 直连真实 PG（连接信息见 application-pg-test.yaml，环境变量可覆盖），使「发消息 → 事件流 →
 * 状态持久化 → 跨轮次上下文恢复」全链路可断言。测试数据落专用租户 999999，
 * 每个测试结束后按租户精确清理，不影响共享库中 local 环境的手动数据。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = BasePgDbAndRedisUnitTest.Application.class)
@ActiveProfiles("pg-test") // 设置使用 application-pg-test 配置文件
@Sql(scripts = "/sql/pg/clean.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD) // 每个测试结束后，按测试租户清理 DB
public abstract class BasePgDbAndRedisUnitTest {

    @Import({
            // DB 配置类
            NexaiDataSourceAutoConfiguration.class, // 自己的 DB 配置类
            DataSourceAutoConfiguration.class, // Spring DB 自动配置类
            DataSourceTransactionManagerAutoConfiguration.class, // Spring 事务自动配置类
            DruidDataSourceAutoConfigure.class, // Druid 自动配置类
            SqlInitializationTestConfiguration.class, // SQL 初始化
            // MyBatis 配置类
            NexaiMybatisAutoConfiguration.class, // 自己的 MyBatis 配置类
            MybatisPlusAutoConfiguration.class, // MyBatis Plus 的自动配置类

            // Redis 配置类
            RedisTestConfiguration.class, // Redis 测试配置类，用于启动 RedisServer
            NexaiRedisAutoConfiguration.class, // 自己的 Redis 配置类
            DataRedisAutoConfiguration.class, // Spring Redis 自动配置类
            RedissonAutoConfigurationV4.class, // Redisson 自动配置类

            // 其它配置类
            SpringUtil.class
    })
    public static class Application {
    }

}
