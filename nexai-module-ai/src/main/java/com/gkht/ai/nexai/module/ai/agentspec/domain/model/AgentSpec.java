package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecPublishWithoutDraftException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecSelfMountingException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecVersionNotExistsException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 智能体规格聚合根（充血模型，零框架依赖）：描述一个智能体的声明式配置，有版本，是图纸而非实体。
 *
 * <p>发布语义状态机：草稿（draft）→ 发布（锁定为不可变版本快照，默认版本指针前移，草稿清空）
 * → 再编辑（生成新草稿，内容默认延续最新提交）。默认版本指针与最新版本号均以版本号（而非版本记录编号）
 * 表达，使发布在聚合内完全自治——无需等待版本落库回填编号。</p>
 *
 * <p>主体元数据含业务编码 spec_code（创建后不可变——数据库主键不外溢到文件系统/日志/运行时标识，
 * workspace 目录段与装配 agentName 用它）与归属层级（决定可见性、编辑权与 workspace 布局，
 * 同样创建后不可变）；二者均不进版本快照（ADR-0006 决策 7/8）。</p>
 */
public class AgentSpec {

    /** 名称长度上限（字符） */
    static final int NAME_MAX_LENGTH = 64;
    /** 图标长度上限（字符） */
    static final int ICON_MAX_LENGTH = 128;
    /** 业务编码格式：小写字母开头，小写字母/数字/连字符组成，总长 2~64 */
    static final Pattern SPEC_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");

    /** 编号，未落库时为 null */
    private Long id;
    /** 规格名称（管理元数据；给 LLM 的自描述在 AgentSpecConfig 内、随版本快照固化） */
    private String name;
    /** 业务编码（slug），创建后不可变：workspace 目录段与装配 agentName 的标识来源 */
    private final String specCode;
    /** 图标标识，可空 */
    private String icon;
    /** 归属层级，创建后不可变 */
    private final OwnerLevel ownerLevel;
    /** 归属用户编号（用户级 = 创建者），非用户级为 null，创建后不可变 */
    private final Long ownerUserId;
    /** 已发布的最新版本号，从未发布为 0 */
    private int latestVersionNo;
    /** 当前默认版本号（会话默认绑定的版本），从未发布为 null */
    private Integer currentVersionNo;
    /** 草稿配置，null 表示当前无草稿（刚发布过或从未创建） */
    private AgentSpecConfig draft;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private AgentSpec(Long id, String name, String specCode, String icon, OwnerLevel ownerLevel,
                      Long ownerUserId, int latestVersionNo, Integer currentVersionNo,
                      AgentSpecConfig draft, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.specCode = specCode;
        this.icon = icon;
        this.ownerLevel = ownerLevel;
        this.ownerUserId = ownerUserId;
        this.latestVersionNo = latestVersionNo;
        this.currentVersionNo = currentVersionNo;
        this.draft = draft;
        this.createTime = createTime;
    }

    /**
     * 创建规格，携带首个草稿
     *
     * @param name       规格名称，不能为空白
     * @param specCode   业务编码（slug），须匹配 {@code ^[a-z][a-z0-9-]{1,63}$}
     * @param icon       图标标识，可空
     * @param ownerLevel 归属层级，不能为 null
     * @param ownerUserId 归属用户编号，用户级必填（创建者），其余层级须为 null
     * @param draft      首个草稿配置，不能为 null
     */
    public static AgentSpec create(String name, String specCode, String icon,
                                   OwnerLevel ownerLevel, Long ownerUserId, AgentSpecConfig draft) {
        validateProfile(name, icon);
        validateIdentity(specCode, ownerLevel, ownerUserId);
        if (draft == null) {
            throw new IllegalArgumentException("新规格必须携带初始草稿");
        }
        return new AgentSpec(null, name.strip(), specCode.strip(), normalizeNullable(icon),
                ownerLevel, ownerUserId, 0, null, draft, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static AgentSpec reconstitute(Long id, String name, String specCode, String icon,
                                         OwnerLevel ownerLevel, Long ownerUserId, int latestVersionNo,
                                         Integer currentVersionNo, AgentSpecConfig draft,
                                         LocalDateTime createTime) {
        return new AgentSpec(id, name, specCode, icon, ownerLevel, ownerUserId,
                latestVersionNo, currentVersionNo, draft, createTime);
    }

    /**
     * 编辑规格：更新主体信息并覆盖草稿。无草稿时即「再编辑生成新草稿」（发布后的迭代入口），
     * 草稿内容以本次全量提交为准。spec_code 与归属层级不可变，不在编辑面内。
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
     * 身份字段共用校验：spec_code 格式与归属层级一致性（用户级必须携带归属用户）
     */
    private static void validateIdentity(String specCode, OwnerLevel ownerLevel, Long ownerUserId) {
        if (specCode == null || !SPEC_CODE_PATTERN.matcher(specCode).matches()) {
            throw new IllegalArgumentException(
                    "业务编码必须为小写字母开头的小写字母/数字/连字符组合（2~64 位）");
        }
        if (ownerLevel == null) {
            throw new IllegalArgumentException("规格必须声明归属层级");
        }
        if (ownerLevel == OwnerLevel.USER && ownerUserId == null) {
            throw new IllegalArgumentException("用户级规格必须携带归属用户");
        }
        if (ownerLevel != OwnerLevel.USER && ownerUserId != null) {
            throw new IllegalArgumentException("非用户级规格不携带归属用户");
        }
    }

    /**
     * 编辑权判定（ADR-0006 决策 8）：用户级仅归属用户可编辑；租户级租户内可编辑
     * （租户隔离由查询侧保证）；平台级仅平台运营方（M2+ 引入运营入口后收紧）。
     *
     * @param userId 当前登录用户编号，无登录态为 null
     */
    public boolean editableBy(Long userId) {
        return ownerLevel != OwnerLevel.USER || Objects.equals(ownerUserId, userId);
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

    public String getSpecCode() {
        return specCode;
    }

    public String getIcon() {
        return icon;
    }

    public OwnerLevel getOwnerLevel() {
        return ownerLevel;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
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
