package com.gkht.ai.nexai.module.ai.skill.domain.model;

import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillVersionImmutableException;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 技能版本实体（不可变）：发布时从草稿固化出的 SKILL.md + 资源全量快照，落库后永不修改。
 *
 * <p>快照存 SKILL.md 原文（front matter 完整保留），运行时经官方解析器重建 AgentSkill；
 * {@link #modify()} 是唯一显式守护——任何修改尝试都被拒绝。</p>
 */
public final class SkillVersion {

    /** 编号，未落库时为 null */
    private final Long id;
    /** 所属技能编号（外部聚合引用） */
    private final Long skillId;
    /** 版本号，技能内从 1 递增 */
    private final int versionNo;
    /** 技能内容快照（SKILL.md 原文 + 附属资源，不可变） */
    private final SkillContent content;
    /** 发布说明，可空 */
    private final String remark;
    /** 发布时间，由持久化填充，新建时为 null */
    private final LocalDateTime createTime;

    private SkillVersion(Long id, Long skillId, int versionNo, SkillContent content,
                         String remark, LocalDateTime createTime) {
        this.id = id;
        this.skillId = skillId;
        this.versionNo = versionNo;
        this.content = content;
        this.remark = remark;
        this.createTime = createTime;
    }

    /**
     * 发布新版本（聚合根 Skill#publish 专用）：固化草稿快照
     */
    static SkillVersion create(Long skillId, int versionNo, SkillContent content, String remark) {
        if (skillId == null) {
            throw new IllegalArgumentException("版本必须归属一个技能");
        }
        if (content == null) {
            throw new IllegalArgumentException("版本快照不能为空");
        }
        return new SkillVersion(null, skillId, versionNo, content, normalizeRemark(remark), null);
    }

    /**
     * 从持久化数据重建版本（Repository 专用，字段原样恢复）
     */
    public static SkillVersion reconstitute(Long id, Long skillId, int versionNo,
                                            SkillContent content, String remark,
                                            LocalDateTime createTime) {
        return new SkillVersion(id, skillId, versionNo, content, remark, createTime);
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
        throw new SkillVersionImmutableException(skillId, versionNo);
    }

    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public SkillContent getContent() {
        return content;
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
        if (!(o instanceof SkillVersion other)) {
            return false;
        }
        // 版本按（技能 + 版本号）这一业务键判等；未落库的版本只与自身相等
        return skillId != null && skillId.equals(other.skillId) && versionNo == other.versionNo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillId, versionNo);
    }

}
