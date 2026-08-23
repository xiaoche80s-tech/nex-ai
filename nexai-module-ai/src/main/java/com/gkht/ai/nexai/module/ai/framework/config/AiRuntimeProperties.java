package com.gkht.ai.nexai.module.ai.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 运行时属性（nexai.ai.runtime）：workspace 根目录与沙箱能力 → Docker 镜像映射。
 * 镜像映射放服务级 yaml（工单 07：能力 SHELL/PYTHON/NODE → 镜像查表，缺省 SHELL 用通用执行镜像）。
 *
 * <p>{@code @Component} 使本类可被组件扫描发现并注册（{@code @ConfigurationProperties}
 * 绑定 nexai.ai.runtime 前缀）；测试经 {@code @Import} 或组件扫描同样生效。</p>
 */
@Component
@ConfigurationProperties(prefix = "nexai.ai.runtime")
@Data
public class AiRuntimeProperties {

    /** workspace 根目录（默认 ~/.agentscope/nexai/workspace），per-spec 目录在此之下按归属层级展开 */
    private Workspace workspace = new Workspace();

    /** skill 物化根目录（默认 ~/.agentscope/nexai/skills），按归属层级展开（工单 10） */
    private Skills skills = new Skills();

    /** 沙箱能力 → Docker 镜像映射（key = 能力名，value = 镜像 tag） */
    private Map<String, String> sandboxImages = new LinkedHashMap<>();

    /** workspace 子配置 */
    @Data
    public static class Workspace {

        /** 根目录字符串（支持 ~ 展开），默认 ~/.agentscope/nexai/workspace */
        private String root = "~/.agentscope/nexai/workspace";

        /** 解析后的根目录（~ 展开为 home） */
        public Path resolvedRoot() {
            return expandHome(root);
        }
    }

    /** skill 物化子配置 */
    @Data
    public static class Skills {

        /** 根目录字符串（支持 ~ 展开），默认 ~/.agentscope/nexai/skills */
        private String root = "~/.agentscope/nexai/skills";

        /** 解析后的根目录（~ 展开为 home） */
        public Path resolvedRoot() {
            return expandHome(root);
        }
    }

    /** ~ 展开为 home 目录并规范化 */
    private static Path expandHome(String value) {
        String resolved = value;
        if (resolved.startsWith("~/")) {
            resolved = System.getProperty("user.home") + resolved.substring(1);
        }
        return Paths.get(resolved).toAbsolutePath().normalize();
    }
}
