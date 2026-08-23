package com.gkht.ai.nexai.module.ai.session.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时装配服务级配置（ADR-0007）：workspace 根目录与沙箱镜像映射——
 * 均为环境事实（部署形态差异），不进规格版本快照。网关与镜像解析读取此配置。
 */
@Component
@ConfigurationProperties(prefix = "nexai.ai.runtime")
public class AiRuntimeProperties {

    /** workspace 布局与落盘配置 */
    private Workspace workspace = new Workspace();

    /** 沙箱镜像映射配置 */
    private Sandbox sandbox = new Sandbox();

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public Sandbox getSandbox() {
        return sandbox;
    }

    public void setSandbox(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    /** workspace 配置：根目录（per-spec 常驻目录挂其下，目录不删除——清理为 M2/M3 运维责任） */
    public static class Workspace {

        /** workspace 根目录，默认用户主目录下 ~/.agentscope/nexai/workspace */
        private Path root = Path.of(System.getProperty("user.home"),
                ".agentscope", "nexai", "workspace");

        public Path getRoot() {
            return root;
        }

        public void setRoot(Path root) {
            this.root = expandHome(root);
        }

        /**
         * 展开起始 {@code ~} 段为用户主目录——yaml 中 {@code ~/...} 是自然写法，
         * 但 Path 属性不会自动展开（落成进程工作目录下的字面 ~ 目录）
         */
        private static Path expandHome(Path path) {
            if (path == null || path.isAbsolute() || path.getNameCount() == 0
                    || !"~".equals(path.getName(0).toString())) {
                return path;
            }
            Path home = Path.of(System.getProperty("user.home"));
            return path.getNameCount() == 1 ? home
                    : home.resolve(path.subpath(1, path.getNameCount()));
        }

    }

    /** 沙箱配置：能力组合 → 镜像映射（环境可覆盖，如内网 registry） */
    public static class Sandbox {

        /**
         * 能力组合 → 镜像。key 为能力组合（去重后按名称字母序 join "-"，
         * 如 shell / python / python-shell / node-python-shell），
         * 或固定 key {@code default}（组合无精确映射时兜底）。未配置时使用内置默认表。
         */
        private Map<String, String> images = new LinkedHashMap<>();

        public Map<String, String> getImages() {
            return images;
        }

        public void setImages(Map<String, String> images) {
            this.images = images;
        }

    }

}
