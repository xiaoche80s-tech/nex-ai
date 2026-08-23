package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverter;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecVersionDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 智能体规格 Repository 实现：规格聚合根与其不可变版本快照的 DO ↔ 领域模型适配。
 * 聚合重建（reconstitute）与主体更新随版本发布工单启用；版本快照仅插入与查询（不可变）。
 */
@Repository
public class AgentSpecRepositoryImpl implements AgentSpecRepository {

    @Resource
    private AgentSpecMapper agentSpecMapper;

    @Resource
    private AgentSpecVersionMapper agentSpecVersionMapper;

    @Resource
    private AgentSpecConverter agentSpecConverter;

    @Override
    public Long save(AgentSpec spec) {
        AgentSpecDO dataObject = agentSpecConverter.toDataObject(spec);
        agentSpecMapper.insert(dataObject);
        return dataObject.getId();
    }

    @Override
    public AgentSpec findById(Long id) {
        AgentSpecDO dataObject = agentSpecMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public Integer findMaxVersionNo(Long specId) {
        return agentSpecVersionMapper.selectMaxVersionNo(specId);
    }

    @Override
    public Long saveVersion(AgentSpecVersion version) {
        AgentSpecVersionDO dataObject = agentSpecConverter.toVersionDataObject(version);
        agentSpecVersionMapper.insert(dataObject);
        return dataObject.getId();
    }

    @Override
    public List<AgentSpecVersion> listVersions(Long specId) {
        return agentSpecVersionMapper.selectListBySpecId(specId).stream()
                .map(this::reconstituteVersion).toList();
    }

    @Override
    public boolean existsVersion(Long specId, int versionNo) {
        return agentSpecVersionMapper.existsBySpecAndVersion(specId, versionNo);
    }

    @Override
    public void update(AgentSpec spec) {
        agentSpecMapper.updateById(agentSpecConverter.toDataObject(spec));
    }

    private AgentSpec reconstitute(AgentSpecDO dataObject) {
        OwnerLevel ownerLevel = OwnerLevel.valueOf(dataObject.getOwnerLevel());
        return AgentSpec.reconstitute(dataObject.getId(), dataObject.getName(), dataObject.getSpecCode(),
                dataObject.getIcon(), ownerLevel, dataObject.getOwnerUserId(),
                agentSpecConverter.jsonToConfig(dataObject.getDraft()),
                dataObject.getCurrentVersionNo(), dataObject.getCreateTime());
    }

    private AgentSpecVersion reconstituteVersion(AgentSpecVersionDO dataObject) {
        return AgentSpecVersion.reconstitute(dataObject.getId(), dataObject.getSpecId(),
                dataObject.getVersionNo(), agentSpecConverter.jsonToConfig(dataObject.getConfig()),
                dataObject.getNote(), dataObject.getCreateTime());
    }

}
