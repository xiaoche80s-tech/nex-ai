package com.gkht.ai.nexai.module.ai.channel.domain.model;

import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ApiKeyMasker;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 渠道聚合根（充血模型，零框架依赖）：一个模型服务接入点——提供商类型、端点地址与密钥（BYOK）。
 * 其下挂 Model 实体（模型元数据），删除渠道级联删除其下模型。
 *
 * <p>密钥仅在聚合内以明文存在，落库时由基础设施层加密（EncryptTypeHandler），
 * 对外展示一律走 {@link #maskedApiKey()}。</p>
 */
public class Channel {

    /** 名称长度上限（字符） */
    static final int NAME_MAX_LENGTH = 64;
    /** 端点地址长度上限（字符） */
    static final int BASE_URL_MAX_LENGTH = 512;
    /** 密钥长度上限（字符） */
    static final int API_KEY_MAX_LENGTH = 1024;

    /** 编号，未落库时为 null */
    private Long id;
    /** 渠道名称，同一提供商多渠道时用于区分 */
    private String name;
    /** 提供商类型 */
    private ChannelProvider provider;
    /** 端点地址（http/https） */
    private String baseUrl;
    /** API 密钥明文，仅存于聚合内存与加密列；ollama 等本地服务可为空 */
    private String apiKey;
    /** 是否启用 */
    private boolean enabled;
    /** 归属维度，MVP 固定租户侧（BYOK） */
    private ChannelOwnerType ownerType;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private Channel(Long id, String name, ChannelProvider provider, String baseUrl, String apiKey,
                    boolean enabled, ChannelOwnerType ownerType, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.ownerType = ownerType;
        this.createTime = createTime;
    }

    /**
     * 创建租户渠道，初始为启用状态
     *
     * @param name      渠道名称，不能为空白
     * @param provider  提供商类型，不能为 null
     * @param baseUrl   端点地址，不能为空白
     * @param apiKey    API 密钥，可空（本地服务）
     * @param ownerType 归属维度，不能为 null
     */
    public static Channel create(String name, ChannelProvider provider, String baseUrl,
                                 String apiKey, ChannelOwnerType ownerType) {
        validateBasics(name, provider, baseUrl);
        if (ownerType == null) {
            throw new IllegalArgumentException("渠道归属维度不能为空");
        }
        return new Channel(null, name.strip(), provider, baseUrl.strip(),
                normalizeApiKey(apiKey), true, ownerType, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static Channel reconstitute(Long id, String name, ChannelProvider provider, String baseUrl,
                                       String apiKey, boolean enabled, ChannelOwnerType ownerType,
                                       LocalDateTime createTime) {
        return new Channel(id, name, provider, baseUrl, apiKey, enabled, ownerType, createTime);
    }

    /**
     * 更新渠道基础信息。apiKey 传 null 或空白表示保留原密钥（编辑界面不回传明文，未修改则不覆盖；
     * 密钥只能保留或更换，不存在清除——本地无密钥渠道重建即可）。
     */
    public void update(String name, ChannelProvider provider, String baseUrl, String apiKey) {
        validateBasics(name, provider, baseUrl);
        this.name = name.strip();
        this.provider = provider;
        this.baseUrl = baseUrl.strip();
        String normalized = normalizeApiKey(apiKey);
        if (normalized != null) {
            this.apiKey = normalized;
        }
    }

    /**
     * 创建与更新共用的必填校验
     */
    private static void validateBasics(String name, ChannelProvider provider, String baseUrl) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("渠道名称不能为空");
        }
        if (name.strip().length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("渠道名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        if (provider == null) {
            throw new IllegalArgumentException("渠道提供商类型不能为空");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("渠道端点地址不能为空");
        }
        if (baseUrl.strip().length() > BASE_URL_MAX_LENGTH) {
            throw new IllegalArgumentException("渠道端点地址不能超过 " + BASE_URL_MAX_LENGTH + " 个字符");
        }
    }

    /** 密钥规范化：空白归 null（本地服务无密钥），其余去首尾空白 */
    private static String normalizeApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String stripped = apiKey.strip();
        if (stripped.length() > API_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("API 密钥不能超过 " + API_KEY_MAX_LENGTH + " 个字符");
        }
        return stripped;
    }

    /**
     * 启用渠道
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * 停用渠道：保留数据与密钥，仅退出来用范围
     */
    public void disable() {
        this.enabled = false;
    }

    /**
     * 密钥脱敏展示（规则见 {@link ApiKeyMasker}）
     */
    public String maskedApiKey() {
        return ApiKeyMasker.mask(apiKey);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ChannelProvider getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** 密钥明文：仅供 Repository 与探测网关使用，禁止直接出参 */
    public String getApiKey() {
        return apiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ChannelOwnerType getOwnerType() {
        return ownerType;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Channel other)) {
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
