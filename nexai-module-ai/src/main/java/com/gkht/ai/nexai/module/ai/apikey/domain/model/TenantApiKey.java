package com.gkht.ai.nexai.module.ai.apikey.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 租户 API Key 聚合根（工单 16）：租户开发者生成租户级 API Key，经 OpenAI 兼容接口
 * （/app-api/ai/openai/chat/completions，流式）调用智能体——自有应用改个 base URL 即接入。
 *
 * <p><b>密文存储</b>：落库只存 SHA-256 哈希（keyHash）与识别前缀（keyPrefix，明文展示面）；
 * 明文 Key 仅在生成时返回一次（对齐业界惯例，泄漏面最小）。吊销 = 状态置 REVOKED，
 * 校验即拒绝（泄漏止损）。</p>
 *
 * <p><b>权限边界</b>：仅本租户智能体（租户隔离由租户上下文 + 租户插件保证）+
 * 限规格范围（specCodes 白名单，空 = 本租户全部规格）。</p>
 */
public class TenantApiKey {

    /** Key 名称长度上限 */
    static final int NAME_MAX_LENGTH = 64;
    /** 识别前缀长度（明文 Key 的前 N 字符，展示面） */
    static final int PREFIX_LENGTH = 11;
    /** 规格范围数量上限 */
    static final int SPEC_CODES_MAX_SIZE = 128;

    private Long id;
    /** 归属租户（认证过滤器恢复租户上下文的依据；管理面操作时由租户插件落列） */
    private final Long tenantId;
    private final String name;
    private final String keyPrefix;    // 明文前缀（识别面，如 nexai-abc）
    private final String keyHash;      // SHA-256 hex（校验面）
    private ApiKeyStatus status;
    private final List<String> specCodes; // 规格范围（specCode 白名单），空 = 本租户全部规格
    private java.time.LocalDateTime createTime; // 创建时间（管理元数据，落库回填）

    private TenantApiKey(Long id, Long tenantId, String name, String keyPrefix, String keyHash,
                         ApiKeyStatus status, List<String> specCodes,
                         java.time.LocalDateTime createTime) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.status = status;
        this.specCodes = specCodes;
        this.createTime = createTime;
    }

    /**
     * 生成新 Key（应用层生成明文并计算哈希后由此建档）
     *
     * @param name      Key 名称，不能为空白且最多 {@value NAME_MAX_LENGTH} 字符
     * @param keyPrefix 明文 Key 的识别前缀（非空白）
     * @param keyHash   明文 Key 的 SHA-256 hex（非空白）
     * @param specCodes 规格范围白名单，可空（= 全部），无空白/重复项且最多 {@value SPEC_CODES_MAX_SIZE} 项
     */
    public static TenantApiKey issue(String name, String keyPrefix, String keyHash,
                                     List<String> specCodes) {
        if (name == null || name.isBlank() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("API Key 名称不能为空且最多 " + NAME_MAX_LENGTH + " 个字符");
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("API Key 必须携带识别前缀");
        }
        if (keyHash == null || keyHash.isBlank()) {
            throw new IllegalArgumentException("API Key 必须携带哈希");
        }
        if (specCodes != null && (specCodes.size() > SPEC_CODES_MAX_SIZE
                || specCodes.stream().anyMatch(code -> code == null || code.isBlank())
                || specCodes.stream().distinct().count() != specCodes.size())) {
            throw new IllegalArgumentException("规格范围不能含空白或重复项且最多 "
                    + SPEC_CODES_MAX_SIZE + " 项");
        }
        return new TenantApiKey(null, null, name.strip(), keyPrefix, keyHash, ApiKeyStatus.ENABLED,
                specCodes == null ? List.of() : List.copyOf(specCodes), null);
    }

    /** 落库重建（reconstitute，携带租户归属与创建时间等管理元数据） */
    public static TenantApiKey reconstitute(Long id, Long tenantId, String name, String keyPrefix,
                                            String keyHash, ApiKeyStatus status,
                                            List<String> specCodes,
                                            java.time.LocalDateTime createTime) {
        return new TenantApiKey(id, tenantId, name, keyPrefix, keyHash,
                status == null ? ApiKeyStatus.ENABLED : status,
                specCodes == null ? List.of() : List.copyOf(specCodes), createTime);
    }

    /** 吊销（泄漏止损）：状态单向 ENABLED → REVOKED，幂等 */
    public void revoke() {
        status = ApiKeyStatus.REVOKED;
    }

    /** 校验可用：未吊销 */
    public boolean isActive() {
        return status == ApiKeyStatus.ENABLED;
    }

    /** 权限边界：规格是否在放行范围（空范围 = 本租户全部规格） */
    public boolean allowsSpec(String specCode) {
        return specCodes.isEmpty() || specCodes.contains(specCode);
    }

    public Long getId() {
        return id;
    }

    /** 归属租户（认证路径恢复租户上下文的依据） */
    public Long getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    /** SHA-256 哈希（校验路径用；明文不落库不回传——生成时一次性返回） */
    public String getKeyHash() {
        return keyHash;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public java.time.LocalDateTime getCreateTime() {
        return createTime;
    }

    /** 规格范围（specCode 白名单），空 = 本租户全部规格 */
    public List<String> getSpecCodes() {
        return specCodes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TenantApiKey other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(tenantId, other.tenantId)
                && Objects.equals(name, other.name)
                && Objects.equals(keyPrefix, other.keyPrefix) && Objects.equals(keyHash, other.keyHash)
                && status == other.status && Objects.equals(specCodes, other.specCodes)
                && Objects.equals(createTime, other.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, name, keyPrefix, keyHash, status, specCodes, createTime);
    }

}
