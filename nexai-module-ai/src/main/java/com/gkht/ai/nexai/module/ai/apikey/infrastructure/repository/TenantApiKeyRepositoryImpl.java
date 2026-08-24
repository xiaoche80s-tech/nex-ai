package com.gkht.ai.nexai.module.ai.apikey.infrastructure.repository;

import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.framework.tenant.core.util.TenantUtils;
import com.gkht.ai.nexai.module.ai.apikey.domain.model.ApiKeyStatus;
import com.gkht.ai.nexai.module.ai.apikey.domain.model.TenantApiKey;
import com.gkht.ai.nexai.module.ai.apikey.domain.repository.TenantApiKeyRepository;
import com.gkht.ai.nexai.module.ai.apikey.infrastructure.dataobject.TenantApiKeyDO;
import com.gkht.ai.nexai.module.ai.apikey.infrastructure.mapper.TenantApiKeyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 租户 API Key 仓储实现。findByKeyHash 为认证校验路径（请求初期租户上下文未建立），
 * 经 TenantUtils 忽略租户跨租户哈希寻址——哈希全局唯一，寻址即鉴权（找不到 = 无效 Key）。
 */
@Repository
public class TenantApiKeyRepositoryImpl implements TenantApiKeyRepository {

    @Resource
    private TenantApiKeyMapper tenantApiKeyMapper;

    @Override
    public Long save(TenantApiKey key) {
        TenantApiKeyDO dataObject = toDataObject(key);
        tenantApiKeyMapper.insert(dataObject);
        return dataObject.getId();
    }

    @Override
    public void update(TenantApiKey key) {
        tenantApiKeyMapper.updateById(toDataObject(key));
    }

    @Override
    public TenantApiKey findById(Long id) {
        TenantApiKeyDO dataObject = tenantApiKeyMapper.selectById(id);
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public TenantApiKey findByKeyHash(String keyHash) {
        return TenantUtils.executeIgnore(() -> {
            TenantApiKeyDO dataObject =
                    tenantApiKeyMapper.selectOne(TenantApiKeyDO::getKeyHash, keyHash);
            return dataObject == null ? null : toDomain(dataObject);
        });
    }

    private TenantApiKeyDO toDataObject(TenantApiKey key) {
        TenantApiKeyDO dataObject = new TenantApiKeyDO();
        dataObject.setId(key.getId());
        dataObject.setName(key.getName());
        dataObject.setKeyPrefix(key.getKeyPrefix());
        dataObject.setKeyHash(key.getKeyHash());
        dataObject.setStatus(key.getStatus().name());
        dataObject.setSpecCodes(JsonUtils.toJsonString(key.getSpecCodes()));
        return dataObject;
    }

    private TenantApiKey toDomain(TenantApiKeyDO dataObject) {
        List<String> specCodes = dataObject.getSpecCodes() == null || dataObject.getSpecCodes().isBlank()
                ? List.of()
                : JsonUtils.parseArray(dataObject.getSpecCodes(), String.class);
        return TenantApiKey.reconstitute(dataObject.getId(), dataObject.getTenantId(),
                dataObject.getName(), dataObject.getKeyPrefix(), dataObject.getKeyHash(),
                ApiKeyStatus.valueOf(dataObject.getStatus()), specCodes, dataObject.getCreateTime());
    }

}
