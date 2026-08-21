package com.gkht.ai.nexai.module.ai.model.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.model.domain.model.Model;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ModelRepository;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ModelConverter;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ModelDO;
import com.gkht.ai.nexai.module.ai.model.infrastructure.mapper.ModelMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 模型 Repository 实现：DO ↔ 领域模型适配，聚合重建经 Model.reconstitute。
 */
@Repository
public class ModelRepositoryImpl implements ModelRepository {

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private ModelConverter modelConverter;

    @Override
    public Long save(Model model) {
        ModelDO dataObject = modelConverter.toDataObject(model);
        if (dataObject.getId() == null) {
            modelMapper.insert(dataObject);
        } else {
            modelMapper.updateById(dataObject);
        }
        return dataObject.getId();
    }

    @Override
    public Model findById(Long id) {
        ModelDO dataObject = modelMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public List<Model> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return modelMapper.selectByIds(ids).stream().map(this::reconstitute).toList();
    }

    @Override
    public void deleteById(Long id) {
        modelMapper.deleteById(id);
    }

    @Override
    public boolean existsByChannelIdAndModelId(Long channelId, String modelId, Long excludeId) {
        ModelDO existing = modelMapper.selectByChannelIdAndModelId(channelId, modelId);
        return existing != null && !existing.getId().equals(excludeId);
    }

    private Model reconstitute(ModelDO dataObject) {
        return Model.reconstitute(dataObject.getId(), dataObject.getChannelId(), dataObject.getModelId(),
                dataObject.getName(), dataObject.getContextWindow(), dataObject.getInputPrice(),
                dataObject.getOutputPrice(), modelConverter.capabilitiesFromJson(dataObject.getCapabilities()),
                Boolean.TRUE.equals(dataObject.getEnabled()), dataObject.getCreateTime());
    }

}
