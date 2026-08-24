package com.gkht.ai.nexai.module.ai.session.infrastructure.runtime;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 常驻实例管理器直测（fake 装配器，不依赖 agentscope 真实装配）：缓存命中、版本戳
 * 失效重建、引用计数善后——原经网关测试以 chatModelFactory 调用计数旁路观测的行为，
 * 此处以装配回调直接断言。
 */
class AgentInstanceManagerTest {

    /** 计数装配器：记录每次装配产出（装配次数 = 缓存行为观测面） */
    private static final class CountingAssembler implements AgentInstanceManager.AgentAssembler {
        final List<AssembledAgent> assembled = new ArrayList<>();

        @Override
        public AssembledAgent assemble(AgentRuntimeConfig config) {
            HarnessAgent agent = mock(HarnessAgent.class);
            Mockito.when(agent.getName()).thenReturn("agent-" + assembled.size());
            AssembledAgent result = new AssembledAgent(agent, List.of());
            assembled.add(result);
            return result;
        }
    }

    private static AgentRuntimeConfig config(Channel channel) {
        Model model = mock(Model.class);
        return AgentRuntimeConfig.of("u1", "s1", 1L, "spec-a", "spec-a-v1", 1L, 1,
                OwnerLevel.TENANT, null, null, null, null, null,
                null, null, null, null, channel, model);
    }

    private static Channel channelUpdatedAt(LocalDateTime updateTime) {
        Channel channel = mock(Channel.class);
        Mockito.when(channel.getUpdateTime()).thenReturn(updateTime);
        return channel;
    }

    @Test
    @DisplayName("缓存命中：同一版本戳连续 acquire 只装配一次且返回同一实例")
    void cacheHitAssemblesOnce() {
        CountingAssembler assembler = new CountingAssembler();
        AgentInstanceManager manager = new AgentInstanceManager(assembler);
        AgentRuntimeConfig cfg = config(channelUpdatedAt(null));

        AgentInstanceManager.InstanceEntry first = manager.acquire(cfg);
        AgentInstanceManager.InstanceEntry second = manager.acquire(cfg);
        assertSame(first, second, "同版本戳应命中常驻实例");
        assertEquals(1, assembler.assembled.size(), "只装配一次（不 per-请求新建）");

        manager.release(first);
        manager.release(second);
        // 引用归零但未失效：实例保留复用，不 close
        verify(assembler.assembled.get(0).agent(), after(200).never()).close();
    }

    @Test
    @DisplayName("版本戳失效：渠道更新时间变化触发重建，旧实例引用归零后善后关闭")
    void staleStampRebuildsAndClosesIdle() {
        CountingAssembler assembler = new CountingAssembler();
        AgentInstanceManager manager = new AgentInstanceManager(assembler);

        AgentInstanceManager.InstanceEntry first =
                manager.acquire(config(channelUpdatedAt(null)));
        manager.release(first);

        // 渠道配置热更 → 版本戳变化 → 重建（旧实例已无引用，立即善后）
        AgentInstanceManager.InstanceEntry rebuilt =
                manager.acquire(config(channelUpdatedAt(LocalDateTime.of(2026, 8, 24, 0, 0))));
        assertNotSame(first, rebuilt);
        assertEquals(2, assembler.assembled.size(), "版本戳变化应重新装配");
        verify(assembler.assembled.get(0).agent()).close();
        manager.release(rebuilt);
    }

    @Test
    @DisplayName("引用计数善后：旧实例仍被持有时替换不关闭，最后一个引用释放才关闭")
    void replacedInstanceClosesAfterLastRelease() {
        CountingAssembler assembler = new CountingAssembler();
        AgentInstanceManager manager = new AgentInstanceManager(assembler);

        AgentInstanceManager.InstanceEntry held =
                manager.acquire(config(channelUpdatedAt(null))); // 活动流持有，不 release

        // 版本戳变化重建：旧实例仍有引用 → 不立即 close
        AgentInstanceManager.InstanceEntry rebuilt =
                manager.acquire(config(channelUpdatedAt(LocalDateTime.of(2026, 8, 24, 0, 0))));
        verify(assembler.assembled.get(0).agent(), after(200).never()).close();

        // 最后一个引用释放 → 善后关闭
        manager.release(held);
        verify(assembler.assembled.get(0).agent()).close();
        manager.release(rebuilt);
    }

    @Test
    @DisplayName("peek 不持有引用：命中返回实例，未装配返回 null")
    void peekDoesNotHoldReference() {
        CountingAssembler assembler = new CountingAssembler();
        AgentInstanceManager manager = new AgentInstanceManager(assembler);
        AgentRuntimeConfig cfg = config(channelUpdatedAt(null));

        assertEquals(null, manager.peek(cfg), "未装配时 peek 返回 null");
        AgentInstanceManager.InstanceEntry entry = manager.acquire(cfg);
        assertSame(entry, manager.peek(cfg));
        assertEquals(1, assembler.assembled.size(), "peek 不触发装配");
        manager.release(entry);
    }
}
