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
 * <p>MVP 范围：创建（携带首个草稿）与查询。发布固化为不可变版本快照、默认版本指针等
 * 状态机随版本工单扩展。</p>
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
                ownerLevel, ownerUserId, draft, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static AgentSpec reconstitute(Long id, String name, String specCode, String icon,
                                         OwnerLevel ownerLevel, Long ownerUserId,
                                         AgentSpecConfig draft, LocalDateTime createTime) {
        return new AgentSpec(id, name, specCode, icon, ownerLevel, ownerUserId, draft, createTime);
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
