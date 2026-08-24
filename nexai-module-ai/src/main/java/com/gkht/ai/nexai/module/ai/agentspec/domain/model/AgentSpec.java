package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 智能体规格聚合根（充血模型，零框架依赖）：描述一个智能体的声明式配置，是图纸而非运行实体。
 *
 * <p>主体元数据含业务编码 spec_code（创建后不可变——数据库主键不外溢到文件系统/日志/运行时标识，
 * workspace 目录段与装配 agentName 用它）与归属层级（决定可见性、编辑权与 workspace 布局，
 * 同样创建后不可变）；二者均不进版本快照。</p>
 *
 * <p>版本语义：草稿 {@link #draft} 与已发布不可变快照（{@link AgentSpecVersion}，独立表）
 * 分离——发布把草稿固化为版本快照并推进 {@link #currentVersionNo}（当前版本指针，运行寻址）；
 * 再编辑只改草稿不动快照；切换当前版本仅回退指针。DB 版本快照是唯一权威源，
 * 本地盘仅为其物化缓存。</p>
 */
public class AgentSpec {

    /** 名称长度上限（字符；Command 校验注解共用） */
    public static final int NAME_MAX_LENGTH = 64;
    /** 图标长度上限（字符；Command 校验注解共用） */
    public static final int ICON_MAX_LENGTH = 128;
    /** 业务编码格式：小写字母开头，小写字母/数字/连字符组成，总长 2~64（Command 校验注解共用） */
    public static final String SPEC_CODE_REGEX = "^[a-z][a-z0-9-]{1,63}$";
    static final Pattern SPEC_CODE_PATTERN = Pattern.compile(SPEC_CODE_REGEX);

    /** 编号，未落库时为 null */
    private Long id;
    /** 规格名称（管理元数据） */
    private String name;
    /** 业务编码（slug），创建后不可变：workspace 目录段与装配 agentName 的标识来源 */
    private final String specCode;
    /** 图标标识，可空 */
    private String icon;
    /** 归属层级，创建后不可变 */
    private final OwnerLevel ownerLevel;
    /** 归属用户编号（用户级 = 创建者），非用户级为 null，创建后不可变 */
    private final Long ownerUserId;
    /** 草稿配置，null 表示当前无草稿 */
    private AgentSpecConfig draft;
    /** 当前生效版本号（当前版本指针，运行寻址），null 表示从未发布 */
    private Integer currentVersionNo;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private AgentSpec(Long id, String name, String specCode, String icon, OwnerLevel ownerLevel,
                      Long ownerUserId, AgentSpecConfig draft, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.specCode = specCode;
        this.icon = icon;
        this.ownerLevel = ownerLevel;
        this.ownerUserId = ownerUserId;
        this.draft = draft;
        this.createTime = createTime;
    }

    /**
     * 创建规格，携带首个草稿
     *
     * @param name        规格名称，不能为空白
     * @param specCode    业务编码（slug），须匹配 {@code ^[a-z][a-z0-9-]{1,63}$}
     * @param icon        图标标识，可空
     * @param ownerLevel  归属层级，不能为 null
     * @param ownerUserId 归属用户编号，用户级必填（创建者），其余层级须为 null
     * @param draft       首个草稿配置，不能为 null
     */
    public static AgentSpec create(String name, String specCode, String icon,
                                   OwnerLevel ownerLevel, Long ownerUserId, AgentSpecConfig draft) {
        validateProfile(name, icon);
        validateIdentity(specCode, ownerLevel, ownerUserId);
        if (draft == null) {
            throw new IllegalArgumentException("新规格必须携带初始草稿");
        }
        return new AgentSpec(null, name.strip(), specCode.strip(), normalizeNullable(icon),
                ownerLevel, ownerUserId, draft, null);    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static AgentSpec reconstitute(Long id, String name, String specCode, String icon,
                                         OwnerLevel ownerLevel, Long ownerUserId,
                                         AgentSpecConfig draft, Integer currentVersionNo,
                                         LocalDateTime createTime) {
        AgentSpec spec = new AgentSpec(id, name, specCode, icon, ownerLevel, ownerUserId, draft, createTime);
        spec.currentVersionNo = currentVersionNo;
        return spec;
    }

    /**
     * 发布当前草稿：固化为不可变版本快照并推进当前版本指针。
     *
     * <p>发布校验（补齐草稿态可空的行为性配置）：模型引用必须非空（无模型即无可运行）。
     * 挂载引用的条目存在性由装配期校验（跨聚合只读），此处不校验。</p>
     *
     * @param nextVersionNo 新版本号（现有最大 + 1，由仓储在事务内计算）
     * @param note          发布备注，可空
     * @return 固化后的不可变版本快照
     * @throws IllegalStateException 无草稿或草稿缺模型引用时抛出
     */
    public AgentSpecVersion publish(int nextVersionNo, String note) {
        if (draft == null) {
            throw new IllegalStateException("没有可发布的草稿");
        }
        if (draft.getModelId() == null) {
            throw new IllegalStateException("发布前必须为规格配置模型");
        }
        AgentSpecVersion version = AgentSpecVersion.create(id, nextVersionNo, draft, note);
        currentVersionNo = version.getVersionNo();
        return version;
    }

    /**
     * 用新草稿整体替换当前草稿（编辑面）：只动草稿，不触碰任何已发布快照。
     *
     * @param newDraft 新草稿配置，不能为 null
     */
    public void replaceDraft(AgentSpecConfig newDraft) {
        if (newDraft == null) {
            throw new IllegalArgumentException("草稿配置不能为空");
        }
        this.draft = newDraft;
    }

    /**
     * 更新主体元数据（名称/图标，编辑面）：spec_code 与归属创建后不可变，不在此面。
     *
     * @param newName 新名称，不能为空白
     * @param newIcon 新图标标识，可空
     */
    public void updateProfile(String newName, String newIcon) {
        validateProfile(newName, newIcon);
        this.name = newName.strip();
        this.icon = normalizeNullable(newIcon);
    }

    /**
     * 切换当前生效版本（回退指针）：以传入的目标版本对象为准——存在性由调用方
     * 解析出对象即证明（悬空指针在解析处报错），本方法只校验目标确实归属本聚合。
     *
     * @param target 目标版本快照（须归属本规格）
     * @throws IllegalStateException 目标版本不属于本规格时抛出
     */
    public void switchToVersion(AgentSpecVersion target) {
        if (target == null || !Objects.equals(target.getSpecId(), id)) {
            throw new IllegalStateException(
                    "版本 " + (target == null ? null : target.getVersionNo()) + " 不存在");
        }
        this.currentVersionNo = target.getVersionNo();
    }

    /** 当前生效版本号（当前版本指针），null 表示从未发布 */
    public Integer getCurrentVersionNo() {
        return currentVersionNo;
    }

    /** 是否已有已发布版本 */
    public boolean hasPublishedVersion() {
        return currentVersionNo != null;
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

    /** 可空文本规范化：空白归 null，其余去首尾空白（收敛于 {@link NullableTexts}） */
    private static String normalizeNullable(String value) {
        return NullableTexts.normalizeNullable(value);
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
