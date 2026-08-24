package com.gkht.ai.nexai.module.ai.framework.config;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenTelemetry SDK 接线（工单 14）：按 {@code nexai.ai.observability.otel.enabled} 开关
 * 初始化全局 OTel SDK（默认关闭——无 SDK 时框架 {@code OtelTracingMiddleware} 全部 no-op
 * 近零开销）。导出器与采样<b>可配</b>：经 {@code otel.*} 标准属性（yaml / 环境变量），
 * 如 {@code otel.traces.exporter=otlp} + {@code otel.exporter.otlp.traces.endpoint} +
 * {@code otel.traces.sampler=parentbased_traceidratio} + {@code otel.traces.sampler.arg=0.1}。
 *
 * <p>OTel SDK autoconfigure 原生只读系统属性/环境变量，本配置桥接 Spring
 * {@link Environment}（含 application.yaml 的 {@code otel.*} 键）后初始化——
 * 全局一次性（JVM 单 SDK），重复初始化由静态标志短路。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "nexai.ai.observability.otel", name = "enabled",
        havingValue = "true")
public class AiOtelConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiOtelConfiguration.class);

    /** 全局一次性初始化标志（GlobalOpenTelemetry 不可重复 set） */
    private static volatile boolean initialized = false;

    private final ConfigurableEnvironment environment;

    public AiOtelConfiguration(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void initializeGlobalOtelSdk() {
        if (initialized) {
            return;
        }
        synchronized (AiOtelConfiguration.class) {
            if (initialized) {
                return;
            }
            Map<String, String> otelProperties = collectOtelProperties();
            AutoConfiguredOpenTelemetrySdk.builder()
                    .addPropertiesSupplier(() -> otelProperties)
                    .setResultAsGlobal()
                    .build();
            initialized = true;
            log.info("OpenTelemetry SDK 已初始化（otel.* 属性 {} 项，导出器与采样由其决定）",
                    otelProperties.size());
        }
    }

    /** 桥接 Spring Environment 中的 otel.* 属性（yaml 配置进 SDK autoconfigure） */
    private Map<String, String> collectOtelProperties() {
        Map<String, String> properties = new HashMap<>();
        environment.getPropertySources().stream()
                .filter(source -> source instanceof EnumerablePropertySource<?>)
                .map(source -> (EnumerablePropertySource<?>) source)
                .forEach(source -> {
                    for (String name : source.getPropertyNames()) {
                        if (name.startsWith("otel.") && !properties.containsKey(name)) {
                            Object value = source.getProperty(name);
                            if (value != null) {
                                properties.put(name, String.valueOf(value));
                            }
                        }
                    }
                });
        return properties;
    }

}
