package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecVersionImmutableException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 智能体规格版本实体（不可变）：发布时从草稿固化出的全量快照，落库后永不修改。
 *
 * <p>类上无任何变更行为，{@link #modify(AgentSpecConfig)} 是唯一的显式守护——
 * 任何修改尝试都被拒绝并抛出 {@link AgentSpecVersionImmutableException}；
 * 迭代请走「编辑草稿 → 发布新版本」。</p>
 */
public final class AgentSpecVersion {

    /** 编号，未落库时为 null */
    private final Long id;
    /** 所属规格编号（外部聚合引用） */
    private final Long specId;
    /** 版本号，规格内从 1 递增 */
    private final int versionNo;
    /** 全量配置快照（不可变） */
    private final AgentSpecConfig config;
    /** 发布说明，可空 */
    private final String remark;
    /** 发布时间，由持久化填充，新建时为 null */
    private final LocalDateTime createTime;

    private AgentSpecVersion(Long id, Long specId, int versionNo, AgentSpecConfig config,
                             String remark, LocalDateTime createTime) {
        this.id = id;
        this.specId = specId;
        this.versionNo = versionNo;
        this.config = config;
        this.remark = remark;
        this.createTime = createTime;
    }

    /**
     * 发布新版本（聚合根 AgentSpec#publish 专用）：固化草稿快照
     */
    static AgentSpecVersion create(Long specId, int versionNo, AgentSpecConfig config, String remark) {
        if (specId == null) {
            throw new IllegalArgumentException("版本必须归属一个规格");
        }
        if (config == null) {
            throw new IllegalArgumentException("版本快照不能为空");
        }
        return new AgentSpecVersion(null, specId, versionNo, config, normalizeRemark(remark), null);
    }

    /**
     * 从持久化数据重建版本（Repository 专用，字段原样恢复）
     */
    public static AgentSpecVersion reconstitute(Long id, Long specId, int versionNo,
                                                AgentSpecConfig config, String remark,
                                                LocalDateTime createTime) {
        return new AgentSpecVersion(id, specId, versionNo, config, remark, createTime);
    }

    private static String normalizeRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            return null;
        }
        return remark.strip();
    }

    /**
     * 已发布版本不可变：任何修改尝试都被拒绝（发布语义的核心不变量）。
     * 该方法的存在使「不可变」成为显式 API 契约而非隐式约定——测试据此断言拒绝行为。
     */
    public void modify() {
        throw new AgentSpecVersionImmutableException(specId, versionNo);
    }

    public Long getId() {
        return id;
    }

    public Long getSpecId() {
        return specId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public AgentSpecConfig getConfig() {
        return config;
    }

    public String getRemark() {
        return remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentSpecVersion other)) {
            return false;
        }
        // 版本按（规格 + 版本号）这一业务键判等；未落库的版本只与自身相等
        return specId != null && specId.equals(other.specId) && versionNo == other.versionNo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(specId, versionNo);
    }

}
