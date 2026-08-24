package com.gkht.ai.nexai.module.ai.usage.infrastructure.repository;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.usage.domain.model.ModelUsage;
import com.gkht.ai.nexai.module.ai.usage.domain.repository.ModelUsageRepository;
import com.gkht.ai.nexai.module.ai.usage.infrastructure.dataobject.ModelUsageDO;
import com.gkht.ai.nexai.module.ai.usage.infrastructure.mapper.ModelUsageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型用量仓储实现：逐条追加（每次模型调用一条）。新事务独立提交（REQUIRES_NEW）
 * ——用量旁路不随会话流回滚。
 */
@Repository
public class ModelUsageRepositoryImpl implements ModelUsageRepository {

    @Resource
    private ModelUsageMapper modelUsageMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void append(ModelUsage usage) {
        if (usage == null) {
            return;
        }
        modelUsageMapper.insert(toDataObject(usage));
    }

    private ModelUsageDO toDataObject(ModelUsage usage) {
        ModelUsageDO dataObject = new ModelUsageDO();
        dataObject.setSessionKey(usage.getSessionKey());
        dataObject.setSpecId(usage.getSpecId());
        dataObject.setVersionNo(usage.getVersionNo());
        dataObject.setAgentId(usage.getAgentId());
        dataObject.setUserId(usage.getUserId());
        dataObject.setModelName(usage.getModelName());
        dataObject.setMessageCount(usage.getMessageCount());
        dataObject.setInputTokens(usage.getInputTokens());
        dataObject.setOutputTokens(usage.getOutputTokens());
        dataObject.setCachedTokens(usage.getCachedTokens());
        dataObject.setTotalTokens(usage.getTotalTokens());
        dataObject.setDurationSeconds(usage.getDurationSeconds());
        dataObject.setOccurredAt(usage.getOccurredAt());
        dataObject.setTenantId(TenantContextHolder.getTenantId() == null
                ? usage.getTenantId() : TenantContextHolder.getTenantId());
        return dataObject;
    }

}
