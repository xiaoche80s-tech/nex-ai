package com.gkht.ai.nexai.module.ai.agentspec.domain.repository;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;

/**
 * 智能体规格聚合仓储端口（按聚合不按表）。MVP 仅创建路径（save = insert）；
 * 主体字段更新与按编号读取随版本编辑/发布工单扩展；列表查询走轻量读写分离
 * （应用服务经 Mapper 直查转 DTO），不经本端口。
 */
public interface AgentSpecRepository {

    /**
     * 保存聚合：无编号时插入（回填编号）
     *
     * @return 聚合编号
     */
    Long save(AgentSpec spec);

}
