package com.gkht.ai.nexai.module.ai.audit.infrastructure.repository;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.audit.domain.model.AuditEvent;
import com.gkht.ai.nexai.module.ai.audit.domain.model.ToolOutcome;
import com.gkht.ai.nexai.module.ai.audit.domain.repository.AuditEventRepository;
import com.gkht.ai.nexai.module.ai.audit.infrastructure.dataobject.AuditEventDO;
import com.gkht.ai.nexai.module.ai.audit.infrastructure.mapper.AuditEventMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审计事件仓储实现：批量追加（acting 流终止时一次落库）。
 * 采集方（审计 middleware）已持有租户上下文（从 RuntimeContext extra 恢复），
 * 新事务独立提交（REQUIRES_NEW）——审计旁路不随会话流回滚。
 */
@Repository
public class AuditEventRepositoryImpl implements AuditEventRepository {

    @Resource
    private AuditEventMapper auditEventMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void appendAll(List<AuditEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        // 防御：reactor 线程可能缺失租户上下文（调用方已恢复，此处兜底显式落列）
        events.stream()
                .filter(event -> event.getTenantId() != null)
                .map(this::toDataObject)
                .forEach(auditEventMapper::insert);
    }

    private AuditEventDO toDataObject(AuditEvent event) {
        AuditEventDO dataObject = new AuditEventDO();
        dataObject.setSessionKey(event.getSessionKey());
        dataObject.setSpecId(event.getSpecId());
        dataObject.setVersionNo(event.getVersionNo());
        dataObject.setAgentId(event.getAgentId());
        dataObject.setUserId(event.getUserId());
        dataObject.setToolCallId(event.getToolCallId());
        dataObject.setToolName(event.getToolName());
        dataObject.setOutcome(event.getOutcome().name());
        dataObject.setArgumentsDigest(event.getArgumentsDigest());
        dataObject.setResultDigest(event.getResultDigest());
        dataObject.setDurationMs(event.getDurationMs());
        dataObject.setOccurredAt(event.getOccurredAt());
        dataObject.setTenantId(TenantContextHolder.getTenantId() == null
                ? event.getTenantId() : TenantContextHolder.getTenantId());
        return dataObject;
    }

}
