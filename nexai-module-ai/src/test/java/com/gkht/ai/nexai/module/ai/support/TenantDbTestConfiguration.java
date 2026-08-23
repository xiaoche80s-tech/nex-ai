package com.gkht.ai.nexai.module.ai.support;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * H2 集成测试共享配置：租户拦截器装配（向 MybatisPlusInterceptor 注册 TenantLineInnerInterceptor），
 * 使 Mapper 直查与 Repository 写路径均按 TenantContextHolder 自动过滤 tenant_id。
 */
@TestConfiguration
@EnableConfigurationProperties(TenantProperties.class)
public class TenantDbTestConfiguration {

    @Bean
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties properties,
                                                                 MybatisPlusInterceptor interceptor) {
        TenantLineInnerInterceptor inner =
                new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
        MyBatisUtils.addInterceptor(interceptor, inner, 0);
        return inner;
    }

}
