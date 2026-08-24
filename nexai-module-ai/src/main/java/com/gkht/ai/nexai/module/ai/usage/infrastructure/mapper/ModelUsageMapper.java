package com.gkht.ai.nexai.module.ai.usage.infrastructure.mapper;

import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.module.ai.usage.infrastructure.dataobject.ModelUsageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型用量 Mapper（append-only：采集链路只 insert，查询与计价面第三波再立）。
 */
@Mapper
public interface ModelUsageMapper extends BaseMapperX<ModelUsageDO> {
}
