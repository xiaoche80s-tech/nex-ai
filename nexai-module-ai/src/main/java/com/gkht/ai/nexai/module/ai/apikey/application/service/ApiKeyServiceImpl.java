package com.gkht.ai.nexai.module.ai.apikey.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.apikey.application.command.ApiKeyCreateCommand;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.ApiKeyCreatedDTO;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.TenantApiKeyDTO;
import com.gkht.ai.nexai.module.ai.apikey.application.query.ApiKeyPageQuery;
import com.gkht.ai.nexai.module.ai.apikey.domain.model.TenantApiKey;
import com.gkht.ai.nexai.module.ai.apikey.domain.repository.TenantApiKeyRepository;
import com.gkht.ai.nexai.module.ai.shared.util.Hashes;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Collectors;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.API_KEY_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.API_KEY_NOT_EXISTS;

/**
 * 租户 API Key 应用服务实现。明文 Key 生成：{@code nexai-} + 32 字节 SecureRandom
 * base64url（约 43 位），落库仅 SHA-256 哈希 + 识别前缀；吊销为状态单向迁移（幂等）。
 */
@Service
@Validated
public class ApiKeyServiceImpl implements ApiKeyService {

    /** 明文 Key 前缀（识别与品牌口径） */
    static final String KEY_PREFIX = "nexai-";
    /** 识别前缀截取长度（含 KEY_PREFIX 段，列表展示面） */
    static final int PREFIX_DISPLAY_LENGTH = 11;
    /** 随机段字节数（base64url 后约 43 字符） */
    private static final int RANDOM_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private TenantApiKeyRepository tenantApiKeyRepository;

    @Resource
    private com.gkht.ai.nexai.module.ai.apikey.infrastructure.mapper.TenantApiKeyMapper tenantApiKeyMapper;

    @Resource
    private com.gkht.ai.nexai.module.ai.apikey.infrastructure.converter.ApiKeyConverter apiKeyConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyCreatedDTO createKey(ApiKeyCreateCommand command) {
        byte[] random = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(random);
        String plainKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        TenantApiKey key;
        try {
            key = TenantApiKey.issue(command.getName(),
                    plainKey.substring(0, PREFIX_DISPLAY_LENGTH),
                    Hashes.sha256Hex(plainKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    command.getSpecCodes());
        } catch (IllegalArgumentException ex) {
            throw exception(API_KEY_INVALID, ex.getMessage());
        }
        Long id = tenantApiKeyRepository.save(key);
        ApiKeyCreatedDTO dto = new ApiKeyCreatedDTO();
        dto.setId(id);
        dto.setApiKey(plainKey);
        dto.setKeyPrefix(key.getKeyPrefix());
        return dto;
    }

    @Override
    public PageResult<TenantApiKeyDTO> getKeyPage(ApiKeyPageQuery query) {
        // 轻量读写分离：分页经 Mapper 直查 + converter 转 DTO（密文不回传），对齐 Session 模式
        return apiKeyConverter.toDTOPage(tenantApiKeyMapper.selectPage(query, query.getName()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeKey(Long id) {
        TenantApiKey key = tenantApiKeyRepository.findById(id);
        if (key == null) {
            throw exception(API_KEY_NOT_EXISTS);
        }
        key.revoke();
        tenantApiKeyRepository.update(key);
    }

}

