package com.gkht.ai.nexai.module.system.framework.web.config;

import com.gkht.ai.nexai.framework.swagger.config.NexaiSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * system 模块的 web 组件的 Configuration
 *
 * @author 芋道源码
 */
@Configuration(proxyBeanMethods = false)
public class SystemWebConfiguration {

    /**
     * system 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi systemGroupedOpenApi() {
        return NexaiSwaggerAutoConfiguration.buildGroupedOpenApi("system");
    }

}
