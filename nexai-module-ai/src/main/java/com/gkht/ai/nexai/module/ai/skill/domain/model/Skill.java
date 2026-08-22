package com.gkht.ai.nexai.module.ai.skill.domain.model;

import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillPublishWithoutDraftException;
import com.gkht.ai.nexai.module.ai.skill.domain.exception.SkillVersionNotExistsException;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 技能聚合根（充血模型，零框架依赖）：可复用的能力包（SKILL.md 说明 + 附属资源），
 * 可被任意智能体挂载。
 *
 * <p>发布语义与智能体规格同构（tracer bullet 打穿的版本链纪律）：草稿（draft）→
 * 发布（锁定为不可变版本快照，默认版本指针前移，草稿清空）→ 再编辑（生成新草稿）。
 * 运行时仓储只读已发布版本（currentVersionNo 指向的快照），草稿不进运行时。</p>
 */
public class Skill {

    /** 名称长度上限（字符），即 SKILL.md front matter 的 name */
    static final int NAME_MAX_LENGTH = 64;
    /** 描述长度上限（字符），即 SKILL.md front matter 的 description */
    static final int DESCRIPTION_MAX_LENGTH = 512;

    /** 编号，未落库时为 null */
    private Long id;
    /** 技能名（SKILL.md front matter 的 name，运行时挂载与寻址键） */
    private String name;
    /** 技能描述（SKILL.md front matter 的 description，冗余列供列表展示） */
    private String description;
    /** 已发布的最新版本号，从未发布为 0 */
    private int latestVersionNo;
    /** 当前默认版本号（运行时读取的版本），从未发布为 null */
    private Integer currentVersionNo;
    /** 草稿内容，null 表示当前无草稿（刚发布过或从未创建） */
    private SkillContent draft;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private Skill(Long id, String name, String description, int latestVersionNo,
                  Integer currentVersionNo, SkillContent draft, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.latestVersionNo = latestVersionNo;
        this.currentVersionNo = currentVersionNo;
        this.draft = draft;
        this.createTime = createTime;
    }

    /**
     * 创建技能，携带首个草稿
     *
     * @param name        技能名（来自 SKILL.md front matter 解析），不能为空白
     * @param description 技能描述（来自 SKILL.md front matter 解析），不能为空白
     * @param draft       首个草稿内容，不能为 null
     */
    public static Skill create(String name, String description, SkillContent draft) {
        validateProfile(name, description);
        if (draft == null) {
            throw new IllegalArgumentException("新技能必须携带初始草稿");
        }
        return new Skill(null, name.strip(), description.strip(), 0, null, draft, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static Skill reconstitute(Long id, String name, String description, int latestVersionNo,
                                     Integer currentVersionNo, SkillContent draft,
                                     LocalDateTime createTime) {
        return new Skill(id, name, description, latestVersionNo, currentVersionNo, draft, createTime);
    }

    /**
     * 编辑技能：以新草稿全量覆盖（front matter 的 name/description 允许随草稿变更，即改名），
     * 无草稿时即「再编辑生成新草稿」（发布后的迭代入口）。
     */
    public void editDraft(String name, String description, SkillContent draft) {
        validateProfile(name, description);
        if (draft == null) {
            throw new IllegalArgumentException("草稿内容不能为空");
        }
        this.name = name.strip();
        this.description = description.strip();
        this.draft = draft;
    }

    /**
     * 发布当前草稿：固化为不可变版本快照（版本号 = 最新版本号 + 1），默认版本指针前移，草稿清空。
     * 无草稿时抛出 {@link SkillPublishWithoutDraftException}。
     *
     * @param remark 发布说明，可空
     * @return 新版本实体（编号未落库为 null，由 Repository 插入）
     */
    public SkillVersion publish(String remark) {
        if (draft == null) {
            throw new SkillPublishWithoutDraftException(id);
        }
        int nextVersionNo = latestVersionNo + 1;
        SkillVersion version = SkillVersion.create(id, nextVersionNo, draft, remark);
        this.latestVersionNo = nextVersionNo;
        this.currentVersionNo = nextVersionNo;
        this.draft = null;
        return version;
    }

    /**
     * 切换当前默认版本（回滚/迭代入口）：只允许指向本技能已发布的版本号，
     * 越界抛出 {@link SkillVersionNotExistsException}。
     */
    public void switchDefaultVersion(int versionNo) {
        if (versionNo < 1 || versionNo > latestVersionNo) {
            throw new SkillVersionNotExistsException(id, versionNo);
        }
        this.currentVersionNo = versionNo;
    }

    /**
     * 名称与描述共用校验。name 即运行时寻址键，额外禁路径分隔符与 {@code ..}
     * （对齐官方仓储的安全规则——技能会被物化到运行时工作目录）。
     */
    private static void validateProfile(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("技能名称不能为空");
        }
        String trimmedName = name.strip();
        if (trimmedName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("技能名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        if (trimmedName.contains("..") || trimmedName.contains("/") || trimmedName.contains("\\")) {
            throw new IllegalArgumentException("技能名称不能包含路径分隔符或 ..");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("技能描述不能为空");
        }
        if (description.strip().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("技能描述不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
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

    public int getLatestVersionNo() {
        return latestVersionNo;
    }

    public Integer getCurrentVersionNo() {
        return currentVersionNo;
    }

    public SkillContent getDraft() {
        return draft;
    }

    public boolean hasDraft() {
        return draft != null;
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
