package com.gkht.ai.nexai.module.ai.usage.domain.repository;

import com.gkht.ai.nexai.module.ai.usage.domain.model.ModelUsage;

/**
 * 模型用量仓储端口（一个聚合一个）：追加写（append-only），
 * 采集链路逐条落库；查询与计价面第三波再立。
 */
public interface ModelUsageRepository {

    /**
     * 追加一条模型用量（每次模型调用一条）
     *
     * @param usage 模型用量，不能为 null
     */
    void append(ModelUsage usage);

}
