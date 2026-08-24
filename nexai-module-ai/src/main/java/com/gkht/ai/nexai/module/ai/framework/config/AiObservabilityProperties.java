package com.gkht.ai.nexai.module.ai.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 可观测与治理属性（nexai.ai.observability，工单 14）：审计/用量采集开关、
 * 保留期限与脱敏规则默认值可配（spec 决策：默认值可配，先采集不计价；
 * 保留期限清理任务随第三波治理页立项，此处先落配置值）。
 */
@Component
@ConfigurationProperties(prefix = "nexai.ai.observability")
@Data
public class AiObservabilityProperties {

    /** 审计采集配置 */
    private Audit audit = new Audit();

    /** 模型用量采集配置 */
    private Usage usage = new Usage();

    /** OpenTelemetry 追踪开关（true 时初始化全局 OTel SDK，导出器与采样经 otel.* 标准属性配置） */
    private Otel otel = new Otel();

    /** 审计子配置 */
    @Data
    public static class Audit {

        /** 是否采集审计事件（onActing 拦截点 → PG） */
        private boolean enabled = true;

        /** 保留期限（天，仅配置承载；清理任务第三波立项） */
        private int retentionDays = 365;

        /** 摘要截断长度（入参/结果摘要超过即截断） */
        private int maxPayloadLength = 512;

        /** 追加敏感键关键词（小写子串匹配，与内置名单合并；脱敏规则可配） */
        private java.util.List<String> sensitiveKeyKeywords = java.util.List.of();
    }

    /** 用量子配置 */
    @Data
    public static class Usage {

        /** 是否采集模型用量（onModelCall 拦截点 → PG） */
        private boolean enabled = true;

        /** 保留期限（天，仅配置承载；清理任务第三波立项） */
        private int retentionDays = 365;
    }

    /** OpenTelemetry 子配置 */
    @Data
    public static class Otel {

        /**
         * 是否初始化全局 OTel SDK（工单 14：三段 span 经框架 OtelTracingMiddleware 恒挂——
         * 无 SDK 时全部 no-op 近零开销；开启后经 otel.* 标准属性配置导出器与采样，
         * 如 otel.traces.exporter=otlp + otel.exporter.otlp.traces.endpoint）
         */
        private boolean enabled = false;
    }

}
