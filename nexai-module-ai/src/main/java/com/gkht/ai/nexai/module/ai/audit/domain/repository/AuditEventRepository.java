package com.gkht.ai.nexai.module.ai.audit.domain.repository;

import com.gkht.ai.nexai.module.ai.audit.domain.model.AuditEvent;

import java.util.List;

/**
 * 审计事件仓储端口（一个聚合一个，按聚合不按表）：追加写（append-only），
 * 采集链路批量落库；查询面（审计检索页）第三波再立。
 */
public interface AuditEventRepository {

    /**
     * 批量追加审计事件（acting 流终止时一次落库）
     *
     * @param events 审计事件列表，不能为空
     */
    void appendAll(List<AuditEvent> events);

}
