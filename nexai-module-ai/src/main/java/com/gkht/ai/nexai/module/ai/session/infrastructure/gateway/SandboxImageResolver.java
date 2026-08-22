package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.session.framework.config.AiRuntimeProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 沙箱能力 → 镜像解析（ADR-0007 决策 7）：查表顺序 = 显式配置精确组合 →
 * 内置默认表精确组合 → 显式 default → 内置 default。
 *
 * <p>内置默认表按「能力自带 shell」选择镜像（python/node 官方镜像均含 shell），
 * 空能力（纯沙箱文件操作）用 ubuntu；组合无精确映射时落 default，
 * 特殊组合（如 python+node）由部署环境显式配置。</p>
 */
@Component
public class SandboxImageResolver {

    private static final String DEFAULT_KEY = "default";

    /** 内置默认镜像表（环境可经 nexai.ai.runtime.sandbox.images.* 覆盖或补充） */
    private static final Map<String, String> BUILTIN_IMAGES = Map.of(
            "default", "ubuntu:22.04",
            "shell", "ubuntu:22.04",
            "python", "python:3.12-slim",
            "node", "node:22-slim",
            "python-shell", "python:3.12-slim",
            "node-shell", "node:22-slim");

    private final AiRuntimeProperties properties;

    public SandboxImageResolver(AiRuntimeProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析能力组合的沙箱镜像
     *
     * @param capabilities 规格声明的执行能力（可空 = 纯沙箱文件操作）
     */
    public String resolveImage(List<ExecutionCapability> capabilities) {
        String comboKey = comboKey(capabilities);
        Map<String, String> configured = properties.getSandbox().getImages();
        if (configured.containsKey(comboKey)) {
            return configured.get(comboKey);
        }
        if (BUILTIN_IMAGES.containsKey(comboKey)) {
            return BUILTIN_IMAGES.get(comboKey);
        }
        return configured.getOrDefault(DEFAULT_KEY, BUILTIN_IMAGES.get(DEFAULT_KEY));
    }

    /**
     * 能力组合 key：去重后按名称字母序 join "-"（无歧义固定序，与内置表/yaml key 一致，
     * 如 python-shell / node-python-shell）；空组合 → default
     */
    static String comboKey(List<ExecutionCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return DEFAULT_KEY;
        }
        return capabilities.stream().distinct()
                .sorted(java.util.Comparator.comparing(Enum::name))
                .map(c -> c.name().toLowerCase())
                .collect(Collectors.joining("-"));
    }

}
