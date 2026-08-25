package com.gkht.ai.nexai.module.ai.skill.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Skill 资产聚合根（充血模型，零框架依赖）：租户/用户级可复用能力包——
 * 名称/描述/归属层级 + 不可变版本链（{@link SkillVersion}）。管理页上传/编辑形成版本链，
 * 运行时物化为文件目录供 agentscope 文件仓库源读取（ADR-0005 统一资产双来源，
 * Git 同步源导入见工单 29）。
 *
 * <p>版本语义：每次编辑产生新版本（版本号严格递增），只增不改——能力包可追溯、可回退。
 * 当前版本指针 {@link #currentVersionNo} 指向运行时物化采用的版本。</p>
 */
public class Skill {

    /** 名称长度上限（字符），对齐 DDL varchar(255)（工单 26 放宽） */
    static final int NAME_MAX_LENGTH = 255;
    /** 描述长度上限（字符） */
    static final int DESCRIPTION_MAX_LENGTH = 512;

    /** 编号，未落库时为 null */
    private Long id;
    /** 技能名称（agent 引用标识，创建后不可变；同一归属下唯一） */
    private final String name;
    /** 描述 */
    private String description;
    /** 归属层级 */
    private final SkillOwnerLevel ownerLevel;
    /** 归属用户编号（用户级 = 创建者），非用户级为 null */
    private final Long ownerUserId;
    /** 当前生效版本号，null 表示尚无版本 */
    private Integer currentVersionNo;
    /** 是否上架（终端技能目录曝光位，ADR-0006：上架才进终端目录） */
    private boolean published;
    /** Git 同步源编号（软关联 ai_skill_git_source，工单 29 建），自建为 null；只读标记 */
    private final Long gitSourceId;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private Skill(Long id, String name, String description, SkillOwnerLevel ownerLevel,
                  Long ownerUserId, Integer currentVersionNo, boolean published,
                  Long gitSourceId, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerLevel = ownerLevel;
        this.ownerUserId = ownerUserId;
        this.currentVersionNo = currentVersionNo;
        this.published = published;
        this.gitSourceId = gitSourceId;
        this.createTime = createTime;
    }

    /**
     * 创建 skill 资产（首个版本由版本链单独落库）
     */
    public static Skill create(String name, String description, SkillOwnerLevel ownerLevel,
                               Long ownerUserId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("技能名称不能为空");
        }
        if (name.strip().length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("技能名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("技能描述不能为空");
        }
        if (description.strip().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("技能描述不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
        if (ownerLevel == null) {
            throw new IllegalArgumentException("技能必须声明归属层级");
        }
        if (ownerLevel == SkillOwnerLevel.USER && ownerUserId == null) {
            throw new IllegalArgumentException("用户级技能必须携带归属用户");
        }
        if (ownerLevel != SkillOwnerLevel.USER && ownerUserId != null) {
            throw new IllegalArgumentException("非用户级技能不携带归属用户");
        }
        return new Skill(null, name.strip(), description.strip(), ownerLevel, ownerUserId,
                null, false, null, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static Skill reconstitute(Long id, String name, String description,
                                     SkillOwnerLevel ownerLevel, Long ownerUserId,
                                     Integer currentVersionNo, boolean published,
                                     Long gitSourceId, LocalDateTime createTime) {
        return new Skill(id, name, description, ownerLevel, ownerUserId, currentVersionNo,
                published, gitSourceId, createTime);
    }

    /**
     * 登记新版本（编辑产生）：版本号 = 现有最大 + 1，推进当前版本指针。
     *
     * @param nextVersionNo 新版本号（由仓储在事务内计算）
     * @return 不可变版本实体
     */
    public SkillVersion addVersion(int nextVersionNo, SkillContent content, String note) {
        if (content == null) {
            throw new IllegalArgumentException("技能版本必须携带能力包内容");
        }
        SkillVersion version = SkillVersion.create(id, nextVersionNo, content, note);
        currentVersionNo = version.getVersionNo();
        return version;
    }

    /** 更新描述（管理元数据，不触版本链） */
    public void updateProfile(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("技能描述不能为空");
        }
        if (description.strip().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("技能描述不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
        this.description = description.strip();
    }

    /** 推进当前版本指针（版本已落库后调用；创建首版与登记新版本共用） */
    public void advanceCurrentVersion(int versionNo) {
        this.currentVersionNo = versionNo;
    }

    /** 落库回填编号（Repository 专用：insert 后把 DB 生成的主键写回聚合根） */
    public void assignId(Long id) {
        this.id = id;
    }

    /** 上架（进入终端技能目录；版本推进不影响本位，ADR-0005 决策） */
    public void publish() {
        this.published = true;
    }

    /** 下架（移出终端技能目录） */
    public void unpublish() {
        this.published = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public SkillOwnerLevel getOwnerLevel() {
        return ownerLevel;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Integer getCurrentVersionNo() {
        return currentVersionNo;
    }

    public boolean hasVersion() {
        return currentVersionNo != null;
    }

    public boolean isPublished() {
        return published;
    }

    public Long getGitSourceId() {
        return gitSourceId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Skill other)) {
            return false;
        }
        // 聚合根按身份（编号）判等；未落库的聚合只与自身相等
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
