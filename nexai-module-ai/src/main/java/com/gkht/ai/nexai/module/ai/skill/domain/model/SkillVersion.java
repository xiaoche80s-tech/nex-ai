package com.gkht.ai.nexai.module.ai.skill.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Skill 版本实体（不可变）：一次上传/编辑固化的能力包内容（{@link SkillContent}），
 * 版本号按 skill 内严格递增，只增不改——可追溯、可回退。当前版本指针由
 * {@link Skill} 聚合根维护，运行时物化采用当前版本内容。
 */
public class SkillVersion {

    /** 备注长度上限（字符） */
    static final int NOTE_MAX_LENGTH = 255;

    /** 编号，未落库时为 null */
    private Long id;
    /** 所属 skill 编号（聚合根引用） */
    private final Long skillId;
    /** 版本号（skill 内严格递增，1 起） */
    private final Integer versionNo;
    /** 能力包内容（Markdown + 资源） */
    private final SkillContent content;
    /** 版本备注，可空 */
    private final String note;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private SkillVersion(Long id, Long skillId, Integer versionNo, SkillContent content,
                         String note, LocalDateTime createTime) {
        this.id = id;
        this.skillId = skillId;
        this.versionNo = versionNo;
        this.content = content;
        this.note = note;
        this.createTime = createTime;
    }

    /**
     * 创建版本（skillId 未落库时为 null，落库时由仓储回填）
     */
    public static SkillVersion create(Long skillId, Integer versionNo, SkillContent content,
                                      String note) {
        if (versionNo == null || versionNo < 1) {
            throw new IllegalArgumentException("版本号必须为正整数");
        }
        if (content == null) {
            throw new IllegalArgumentException("版本必须携带能力包内容");
        }
        String normalizedNote = note == null || note.isBlank() ? null : note.strip();
        if (normalizedNote != null && normalizedNote.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("版本备注不能超过 " + NOTE_MAX_LENGTH + " 个字符");
        }
        return new SkillVersion(null, skillId, versionNo, content, normalizedNote, null);
    }

    /**
     * 从持久化数据重建实体（Repository 专用，字段原样恢复）
     */
    public static SkillVersion reconstitute(Long id, Long skillId, Integer versionNo,
                                            SkillContent content, String note,
                                            LocalDateTime createTime) {
        return new SkillVersion(id, skillId, versionNo, content, note, createTime);
    }

    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public SkillContent getContent() {
        return content;
    }

    public String getNote() {
        return note;
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
        // 实体按身份（编号）判等；未落库的实体只与自身相等
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
