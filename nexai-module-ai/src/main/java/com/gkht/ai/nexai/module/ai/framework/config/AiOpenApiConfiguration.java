package com.gkht.ai.nexai.module.ai.framework.config;

import com.gkht.ai.nexai.framework.common.enums.WebFilterOrderEnum;
import com.gkht.ai.nexai.framework.security.config.AuthorizeRequestsCustomizer;
import com.gkht.ai.nexai.module.ai.apikey.infrastructure.gateway.ApiKeyAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * OpenAI 兼容出口装配（工单 16）：
 * <ul>
 *   <li>放行出口路径（{@code /app-api/ai/openai/**}）——认证由 {@link ApiKeyAuthFilter}
 *       自行校验（租户 API Key，非登录态）；</li>
 *   <li>注册过滤器（order 在租户上下文过滤器之后、覆盖请求头租户——API Key 即租户凭证）。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class AiOpenApiConfiguration {

    /** 放行 + 认证交接：出口路径不走登录态，由 API Key 过滤器把守 */
    @Bean
    public AuthorizeRequestsCustomizer openAiCompatPermitCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(buildAppApi(ApiKeyAuthFilter.OPENAI_PATH + "/**")).permitAll();
            }
        };
    }

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter() {
        return new ApiKeyAuthFilter();
    }

    /** SSE 帧编码（工单 16）：SseEmitter 的 "data:" 前缀段以 TEXT_PLAIN（无 charset）先行写出，
     *  默认字符串转换器按 ISO-8859-1 写会锁定响应编码（中文变问号）——在转换器链头注册
     *  默认 UTF-8 的字符串转换器，出口流式帧保持 UTF-8 */
    @Bean
    public org.springframework.web.servlet.config.annotation.WebMvcConfigurer openAiSseUtf8Configurer() {
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(
                    java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
                converters.add(0, new org.springframework.http.converter.StringHttpMessageConverter(
                        java.nio.charset.StandardCharsets.UTF_8));
            }
        };
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration(ApiKeyAuthFilter filter) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        // 租户上下文过滤器（-104）之后执行：覆盖请求头 tenant-id，以 Key 归属租户为准
        registration.setOrder(WebFilterOrderEnum.TENANT_CONTEXT_FILTER + 1);
        registration.addUrlPatterns(ApiKeyAuthFilter.OPENAI_FULL_PREFIX + "/*");
        return registration;
    }

}
