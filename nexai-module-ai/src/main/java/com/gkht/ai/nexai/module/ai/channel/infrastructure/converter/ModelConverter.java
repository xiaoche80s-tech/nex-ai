package com.gkht.ai.nexai.module.ai.channel.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ModelDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 模型转换器：领域实体 ↔ DO、领域实体 → 出参 DTO。
 * DO → 领域实体（重建）在 RepositoryImpl 经 reconstitute 完成；
 * channelName 由服务层批量补充（本转换器不触碰渠道聚合）。
 */
@Mapper(componentModel = "spring")
public interface ModelConverter {

    ModelDO toDataObject(Model model);

    @Mapping(target = "channelName", ignore = true)
    ModelDTO toDTO(Model model);

    /** 读路径（Mapper 直查 DO → DTO），channelName 由服务层补充 */
    @Mapping(target = "channelName", ignore = true)
    ModelDTO toDTO(ModelDO modelDO);

    List<ModelDTO> toDTOList(List<ModelDO> list);

    default PageResult<ModelDTO> toDTOPage(PageResult<ModelDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

}
