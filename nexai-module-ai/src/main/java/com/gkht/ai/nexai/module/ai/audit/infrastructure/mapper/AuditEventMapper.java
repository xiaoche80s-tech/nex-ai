package com.gkht.ai.nexai.module.ai.audit.infrastructure.mapper;

import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.module.ai.audit.infrastructure.dataobject.AuditEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计事件 Mapper（append-only：采集链路只 insert，查询面第三波再立）。
 */
@Mapper
public interface AuditEventMapper extends BaseMapperX<AuditEventDO> {
}
