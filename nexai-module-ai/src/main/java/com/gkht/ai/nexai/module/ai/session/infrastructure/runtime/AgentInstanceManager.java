package com.gkht.ai.nexai.module.ai.session.infrastructure.runtime;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderMount;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.SkillMountDirectory;
import io.agentscope.harness.agent.HarnessAgent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 常驻实例管理器（CONTEXT.md 术语，工单 05 基座 + 工单 08 版本戳失效）：
 * 按「规格 + 版本号」（specReference）缓存，版本戳变化时失效重建。
 *
 * <p>快路径/回退分流（工单 08）：MVP 调试会话一律按「默认版本 + 无覆盖」构造装配指令，
 * 命中 {@code specId:versionNo} 缓存即快路径复用常驻实例；装配参数覆盖（非默认版本/
 * 执行环境不符）由应用层构造不同 specReference，自然回落 per-spec 装配——本管理器
 * 不感知分流细节，只按 specReference 缓存并做版本戳失效。</p>
 *
 * <p>版本戳 = 渠道/模型/MCP Server 更新时间 + 技能挂载指纹（skillId@versionNo）+
 * 文件夹挂载指纹 + 规格引用（任一变化即失效）：渠道/模型配置热更、MCP Server 配置变更、
 * 技能推新版本、规格发布/切版本，都使下一次调用重建实例（新会话即用新配置）。</p>
 *
 * <p>实例装配经 {@link AgentAssembler} 回调注入（gateway 的装配翻译），本类只管
 * 生命周期，不感知四层配置翻译——装配行为的变化不再触碰本文件。</p>
 */
public final class AgentInstanceManager {

    /** 装配回调：缓存未命中时由 gateway 的装配翻译构建实例 */
    public interface AgentAssembler {

        /** 按装配指令构建 agent 实例（含需善后的 MCP client 清单） */
        AssembledAgent assemble(AgentRuntimeConfig config);
    }

    private final AgentAssembler assembler;

    private final Map<String, InstanceEntry> instances = new ConcurrentHashMap<>();

    public AgentInstanceManager(AgentAssembler assembler) {
        this.assembler = assembler;
    }

    /** 获取（或构建）实例并持有引用；调用方必须配对 {@link #release} */
    public InstanceEntry acquire(AgentRuntimeConfig config) {
        String key = config.specReference();
        String stamp = versionStamp(config);
        InstanceEntry existing = instances.get(key);
        if (existing != null && !existing.stale && Objects.equals(existing.stamp, stamp)) {
            existing.refs.incrementAndGet();
            return existing;
        }
        // 无实例、已失效（stale）或版本戳不一致：构建新实例（版本戳失效重建），
        // put 无条件替换——旧条目标记失效，引用归零后善后 close
        InstanceEntry fresh = new InstanceEntry(assembler.assemble(config), key, stamp);
        InstanceEntry replaced = instances.put(key, fresh);
        fresh.refs.incrementAndGet();
        if (replaced != null) {
            replaced.stale = true;
            closeIfIdle(replaced);
        }
        return fresh;
    }

    /** 仅查看（不持有引用）：中断定位用，实例不存在返回 null */
    public InstanceEntry peek(AgentRuntimeConfig config) {
        return instances.get(config.specReference());
    }

    /** 释放引用；已失效（版本戳变化被替换）且引用归零时善后 close */
    public void release(InstanceEntry entry) {
        if (entry.refs.decrementAndGet() == 0 && entry.stale) {
            instances.remove(entry.key, entry);
            entry.assembled.closeQuietly();
        }
    }

    /** 释放全部常驻实例（应用停机时调用）：善后关闭，清空注册表 */
    public void closeAll() {
        instances.values().forEach(entry -> entry.assembled.closeQuietly());
        instances.clear();
    }

    /** 被替换的旧实例若已无引用，立即善后 close（幂等：release 路径也会处理） */
    private void closeIfIdle(InstanceEntry entry) {
        if (entry.refs.get() == 0) {
            instances.remove(entry.key, entry);
            entry.assembled.closeQuietly();
        }
    }

    /** 装配指令 → 版本戳（渠道/模型/MCP 更新时间 + 技能/文件夹指纹 + 规格引用），任一变化即失效 */
    private String versionStamp(AgentRuntimeConfig config) {
        long channelStamp = config.getChannel().getUpdateTime() == null
                ? 0L : config.getChannel().getUpdateTime().hashCode();
        long modelStamp = config.getModel().getUpdateTime() == null
                ? 0L : config.getModel().getUpdateTime().hashCode();
        long mcpStamp = config.getMcpServers().stream()
                .mapToLong(server -> server.getUpdateTime() == null
                        ? 0L : server.getUpdateTime().hashCode()).sum();
        String skillStamp = config.getSkillMounts().stream()
                .map(SkillMountDirectory::getFingerprint)
                .reduce("", String::concat);
        // 文件夹挂载经版本发布自然纳入失效（清单固化进版本快照），指纹兜底同版本号下的清单比对
        String folderStamp = config.getFolders().stream()
                .map(FolderMount::fingerprint)
                .reduce("", String::concat);
        return config.specReference() + ":" + channelStamp + ":" + modelStamp
                + ":" + mcpStamp + ":" + skillStamp.hashCode() + ":" + folderStamp.hashCode();
    }

    /**
     * 常驻实例条目：agent 实例 + 引用计数 + 版本戳。acquire 递增计数（活动流持有），
     * release 递减；引用归零且已失效（版本戳变化被替换）时善后 close。
     */
    public static final class InstanceEntry {

        private final AssembledAgent assembled;
        private final String key;
        private final AtomicInteger refs = new AtomicInteger();
        private volatile String stamp;
        private volatile boolean stale;

        InstanceEntry(AssembledAgent assembled, String key, String stamp) {
            this.assembled = assembled;
            this.key = key;
            this.stamp = stamp;
        }

        public HarnessAgent agent() {
            return assembled.agent();
        }
    }
}
