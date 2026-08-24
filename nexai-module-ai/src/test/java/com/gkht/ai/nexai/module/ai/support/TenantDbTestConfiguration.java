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
 *
 * <p>注意：unit-test profile 开启全局懒加载（spring.main.lazy-initialization=true），
 * 本 bean 无被依赖方（挂拦截器是其副作用），不显式急切初始化则永远不创建——
 * 租户过滤静默失效（曾因此漏检跨租户可见性）。故标 {@code @Lazy(false)} 豁免懒加载。</p>
 */
@TestConfiguration
@EnableConfigurationProperties(TenantProperties.class)
public class TenantDbTestConfiguration {

    @Bean
    @org.springframework.context.annotation.Lazy(false)
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties properties,
                                                                 MybatisPlusInterceptor interceptor) {
        TenantLineInnerInterceptor inner =
                new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
        MyBatisUtils.addInterceptor(interceptor, inner, 0);
        return inner;
    }

}
