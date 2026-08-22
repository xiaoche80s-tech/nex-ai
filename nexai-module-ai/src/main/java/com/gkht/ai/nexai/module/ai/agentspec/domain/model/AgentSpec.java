package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecPublishWithoutDraftException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecSelfMountingException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecVersionNotExistsException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 智能体规格聚合根（充血模型，零框架依赖）：描述一个智能体的声明式配置，有版本，是图纸而非实体。
 *
 * <p>发布语义状态机：草稿（draft）→ 发布（锁定为不可变版本快照，默认版本指针前移，草稿清空）
 * → 再编辑（生成新草稿，内容默认延续最新提交）。默认版本指针与最新版本号均以版本号（而非版本记录编号）
 * 表达，使发布在聚合内完全自治——无需等待版本落库回填编号。</p>
 */
public class AgentSpec {

    /** 名称长度上限（字符） */
    static final int NAME_MAX_LENGTH = 64;
    /** 图标长度上限（字符） */
    static final int ICON_MAX_LENGTH = 128;

    /** 编号，未落库时为 null */
    private Long id;
    /** 规格名称（管理元数据；给 LLM 的自描述在 AgentSpecConfig 内、随版本快照固化） */
    private String name;
    /** 图标标识，可空 */
    private String icon;
    /** 已发布的最新版本号，从未发布为 0 */
    private int latestVersionNo;
    /** 当前默认版本号（会话默认绑定的版本），从未发布为 null */
    private Integer currentVersionNo;
    /** 草稿配置，null 表示当前无草稿（刚发布过或从未创建） */
    private AgentSpecConfig draft;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private AgentSpec(Long id, String name, String icon, int latestVersionNo,
                      Integer currentVersionNo, AgentSpecConfig draft, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.latestVersionNo = latestVersionNo;
        this.currentVersionNo = currentVersionNo;
        this.draft = draft;
        this.createTime = createTime;
    }

    /**
     * 创建规格，携带首个草稿
     *
     * @param name  规格名称，不能为空白
     * @param icon  图标标识，可空
     * @param draft 首个草稿配置，不能为 null
     */
    public static AgentSpec create(String name, String icon, AgentSpecConfig draft) {
        validateProfile(name, icon);
        if (draft == null) {
            throw new IllegalArgumentException("新规格必须携带初始草稿");
        }
        return new AgentSpec(null, name.strip(), normalizeNullable(icon), 0, null, draft, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static AgentSpec reconstitute(Long id, String name, String icon, int latestVersionNo,
                                         Integer currentVersionNo, AgentSpecConfig draft,
                                         LocalDateTime createTime) {
        return new AgentSpec(id, name, icon, latestVersionNo, currentVersionNo, draft, createTime);
    }

    /**
     * 编辑规格：更新主体信息并覆盖草稿。无草稿时即「再编辑生成新草稿」（发布后的迭代入口），
     * 草稿内容以本次全量提交为准。
     */
    public void editDraft(String name, String icon, AgentSpecConfig draft) {
        validateProfile(name, icon);
        if (draft == null) {
            throw new IllegalArgumentException("草稿配置不能为空");
        }
        rejectSelfMounting(draft);
        this.name = name.strip();
        this.icon = normalizeNullable(icon);
        this.draft = draft;
    }

    /**
     * 发布当前草稿：固化为不可变版本快照（版本号 = 最新版本号 + 1），默认版本指针前移，草稿清空。
     * 无草稿时抛出 {@link AgentSpecPublishWithoutDraftException}。
     *
     * @param remark 发布说明，可空
     * @return 新版本实体（编号未落库为 null，由 Repository 插入后按需重建）
     */
    public AgentSpecVersion publish(String remark) {
        if (draft == null) {
            throw new AgentSpecPublishWithoutDraftException(id);
        }
        int nextVersionNo = latestVersionNo + 1;
        AgentSpecVersion version = AgentSpecVersion.create(id, nextVersionNo, draft, remark);
        this.latestVersionNo = nextVersionNo;
        this.currentVersionNo = nextVersionNo;
        this.draft = null;
        return version;
    }

    /**
     * 切换当前默认版本（回滚/迭代入口）：只允许指向本规格已发布的版本号，
     * 越界抛出 {@link AgentSpecVersionNotExistsException}。
     */
    public void switchDefaultVersion(int versionNo) {
        if (versionNo < 1 || versionNo > latestVersionNo) {
            throw new AgentSpecVersionNotExistsException(id, versionNo);
        }
        this.currentVersionNo = versionNo;
    }

    /**
     * 主体信息共用校验（name/icon 为管理元数据；行为性配置在 AgentSpecConfig 内自校验）
     */
    private static void validateProfile(String name, String icon) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("规格名称不能为空");
        }
        if (name.strip().length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("规格名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        if (icon != null && icon.strip().length() > ICON_MAX_LENGTH) {
            throw new IllegalArgumentException("图标标识不能超过 " + ICON_MAX_LENGTH + " 个字符");
        }
    }

    /**
     * 拒绝挂载自己（防自引用循环 spawn）：已落库的规格（id 非空）草稿中的子智能体挂载不得指向自身。
     * 新建时尚无编号，无从自引用；A↔B 互挂的循环检测留给 M2 挂载生效工单。
     */
    private void rejectSelfMounting(AgentSpecConfig config) {
        if (id == null) {
            return;
        }
        for (SubagentMount mount : config.getSubagents()) {
            if (id.equals(mount.getSpecId())) {
                throw new AgentSpecSelfMountingException(id);
            }
        }
    }

    /** 可空文本规范化：空白归 null，其余去首尾空白 */
    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public int getLatestVersionNo() {
        return latestVersionNo;
    }

    public Integer getCurrentVersionNo() {
        return currentVersionNo;
    }

    public AgentSpecConfig getDraft() {
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
        if (!(o instanceof AgentSpec other)) {
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
