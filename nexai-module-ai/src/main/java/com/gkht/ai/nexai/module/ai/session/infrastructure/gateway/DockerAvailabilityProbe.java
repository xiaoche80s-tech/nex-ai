package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Docker 可用性探测：沙箱规格装配前置检查（环境无 docker 而规格要求沙箱 → 显式报错不降级，
 * ADR-0007 决策 7）。结果缓存——docker 可用性是慢变环境事实，避免每次装配都起子进程。
 */
@Component
public class DockerAvailabilityProbe {

    private static final Logger log = LoggerFactory.getLogger(DockerAvailabilityProbe.class);

    /** 探测结果缓存时长 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private record CachedResult(boolean available, Instant checkedAt) {
    }

    private final AtomicReference<CachedResult> cache = new AtomicReference<>();

    /**
     * docker CLI 是否可用（daemon 可达）。探测失败按不可用处理（让装配侧给出明确错误）。
     */
    public boolean isAvailable() {
        CachedResult cached = cache.get();
        if (cached != null && cached.checkedAt().isAfter(Instant.now().minus(CACHE_TTL))) {
            return cached.available();
        }
        boolean available = probe();
        cache.set(new CachedResult(available, Instant.now()));
        return available;
    }

    /** 仅测试用：注入探测结果（跳过真实 docker 调用） */
    void override(boolean available) {
        cache.set(new CachedResult(available, Instant.now()));
    }

    private boolean probe() {
        try {
            Process process = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}")
                    .start();
            // 探测超时兜底：卡死的 docker daemon 不应阻塞装配线程太久
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("[dockerProbe][docker info 10 秒未返回，按不可用处理]");
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException ex) {
            // docker CLI 不在 PATH
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
