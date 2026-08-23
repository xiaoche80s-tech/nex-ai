package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverter;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * 智能体规格 Repository 实现：DO ↔ 领域模型适配。MVP 仅创建路径（insert）；
 * 聚合重建（reconstitute）与主体字段更新随版本编辑/发布工单扩展。
 */
@Repository
public class AgentSpecRepositoryImpl implements AgentSpecRepository {

    @Resource
    private AgentSpecMapper agentSpecMapper;

    @Resource
    private AgentSpecConverter agentSpecConverter;

    @Override
    public Long save(AgentSpec spec) {
        AgentSpecDO dataObject = agentSpecConverter.toDataObject(spec);
        agentSpecMapper.insert(dataObject);
        return dataObject.getId();
    }

}
