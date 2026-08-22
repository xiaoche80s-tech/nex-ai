package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecVersionImmutableException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
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
 * 智能体规格 Repository 实现：DO ↔ 领域模型适配，聚合重建经 reconstitute。
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
        if (dataObject.getId() == null) {
            agentSpecMapper.insert(dataObject);
        } else {
            // updateById 默认忽略 null 字段，而发布会把草稿（及可空主体字段）写回 null，故显式全量 set；
            // 首参传空 DO 承接 updater/update_time 自动填充
            agentSpecMapper.update(new AgentSpecDO(), new LambdaUpdateWrapper<AgentSpecDO>()
                    .eq(AgentSpecDO::getId, dataObject.getId())
                    .set(AgentSpecDO::getName, dataObject.getName())
                    .set(AgentSpecDO::getIcon, dataObject.getIcon())
                    .set(AgentSpecDO::getLatestVersionNo, dataObject.getLatestVersionNo())
                    .set(AgentSpecDO::getCurrentVersionNo, dataObject.getCurrentVersionNo())
                    .set(AgentSpecDO::getDraft, dataObject.getDraft()));
        }
        return dataObject.getId();
    }

    @Override
    public AgentSpec findById(Long id) {
        AgentSpecDO dataObject = agentSpecMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public void deleteByIdCascade(Long id) {
        agentSpecMapper.deleteById(id);
        agentSpecVersionMapper.deleteBySpecId(id);
    }

    @Override
    public void createVersion(AgentSpecVersion version) {
        // 端口契约：版本只插入（insert-only）；带编号的版本是已落库的存量记录，更新即违反不可变不变量
        if (version.getId() != null) {
            throw new AgentSpecVersionImmutableException(version.getSpecId(), version.getVersionNo());
        }
        AgentSpecVersionDO dataObject = agentSpecConverter.toVersionDataObject(version);
        agentSpecVersionMapper.insert(dataObject);
    }

    @Override
    public List<AgentSpecVersion> findVersionsBySpecId(Long specId) {
        return agentSpecVersionMapper.selectListBySpecId(specId).stream()
                .map(this::reconstituteVersion).toList();
    }

    @Override
    public AgentSpecVersion findVersion(Long specId, Integer versionNo) {
        AgentSpecVersionDO dataObject = agentSpecVersionMapper.selectBySpecIdAndVersionNo(specId, versionNo);
        return dataObject == null ? null : reconstituteVersion(dataObject);
    }

    private AgentSpec reconstitute(AgentSpecDO dataObject) {
        return AgentSpec.reconstitute(dataObject.getId(), dataObject.getName(),
                dataObject.getIcon(), dataObject.getLatestVersionNo(),
                dataObject.getCurrentVersionNo(), agentSpecConverter.jsonToConfig(dataObject.getDraft()),
                dataObject.getCreateTime());
    }

    private AgentSpecVersion reconstituteVersion(AgentSpecVersionDO dataObject) {
        return AgentSpecVersion.reconstitute(dataObject.getId(), dataObject.getSpecId(),
                dataObject.getVersionNo(), agentSpecConverter.jsonToConfig(dataObject.getSnapshot()),
                dataObject.getRemark(), dataObject.getCreateTime());
    }

}
