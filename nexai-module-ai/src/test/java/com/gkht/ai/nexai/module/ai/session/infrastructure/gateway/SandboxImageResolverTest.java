package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.session.framework.config.AiRuntimeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 沙箱镜像解析纯 JUnit 测试：能力组合 key 的固定字母序（与内置表/yaml key 一致）
 * 与「显式配置 > 内置表 > default」查表顺序。code-review 修复回归：枚举声明序
 * 产生的 shell-python 与内置表 python-shell 错位曾致组合永不命中。
 */
class SandboxImageResolverTest {

    private final SandboxImageResolver resolver = new SandboxImageResolver(new AiRuntimeProperties());

    @Test
    @DisplayName("组合 key 按字母序：与内置表 key 一致（python-shell / node-shell）")
    void comboKeyIsAlphabetical() {
        assertEquals("default", SandboxImageResolver.comboKey(null));
        assertEquals("default", SandboxImageResolver.comboKey(List.of()));
        assertEquals("shell", SandboxImageResolver.comboKey(List.of(ExecutionCapability.SHELL)));
        // 入参顺序无关，key 恒为字母序
        assertEquals("python-shell",
                SandboxImageResolver.comboKey(List.of(ExecutionCapability.SHELL, ExecutionCapability.PYTHON)));
        assertEquals("python-shell",
                SandboxImageResolver.comboKey(List.of(ExecutionCapability.PYTHON, ExecutionCapability.SHELL)));
        assertEquals("node-python-shell", SandboxImageResolver.comboKey(
                List.of(ExecutionCapability.NODE, ExecutionCapability.SHELL, ExecutionCapability.PYTHON)));
    }

    @Test
    @DisplayName("查表顺序：显式配置 > 内置表 > 显式 default > 内置 default")
    void resolvesByPrecedence() {
        assertEquals("python:3.12-slim",
                resolver.resolveImage(List.of(ExecutionCapability.SHELL, ExecutionCapability.PYTHON)),
                "组合命中内置表 python-shell（而非落 default ubuntu）");
        assertEquals("ubuntu:22.04", resolver.resolveImage(null));

        AiRuntimeProperties configured = new AiRuntimeProperties();
        Map<String, String> images = new HashMap<>();
        images.put("python-shell", "registry.internal/python-full:3.12");
        images.put("default", "registry.internal/base:1");
        configured.getSandbox().setImages(images);
        SandboxImageResolver configuredResolver = new SandboxImageResolver(configured);

        assertEquals("registry.internal/python-full:3.12", configuredResolver.resolveImage(
                List.of(ExecutionCapability.PYTHON, ExecutionCapability.SHELL)), "显式组合优先");
        assertEquals("registry.internal/base:1", configuredResolver.resolveImage(
                List.of(ExecutionCapability.NODE, ExecutionCapability.PYTHON)), "无内置项的组合落显式 default");
    }

}
