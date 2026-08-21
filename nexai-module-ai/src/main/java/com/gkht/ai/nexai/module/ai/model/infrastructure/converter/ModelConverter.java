package com.gkht.ai.nexai.module.ai.model.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.model.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.model.domain.model.Model;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ModelDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 模型转换器：领域模型 → DO、DO/领域模型 → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 Model.reconstitute 完成。
 * capabilities 在 DO 侧为 JSON 数组字符串、领域侧为 List，互转集中于此。
 */
@Mapper(componentModel = "spring")
public interface ModelConverter {

    @Mapping(target = "capabilities", source = "capabilities", qualifiedByName = "capabilitiesToJson")
    ModelDO toDataObject(Model model);

    @Mapping(target = "capabilities", source = "capabilities", qualifiedByName = "capabilitiesFromJson")
    ModelDTO toDTO(ModelDO modelDO);

    @Mapping(target = "capabilities", source = "capabilities", qualifiedByName = "capabilitiesPassThrough")
    ModelDTO toDTOFromDomain(Model model);

    List<ModelDTO> toDTOList(List<ModelDO> list);

    default PageResult<ModelDTO> toDTOPage(PageResult<ModelDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    @Named("capabilitiesToJson")
    default String capabilitiesToJson(List<String> capabilities) {
        return JsonUtils.toJsonString(capabilities);
    }

    @Named("capabilitiesFromJson")
    default List<String> capabilitiesFromJson(String capabilities) {
        return JsonUtils.parseArray(capabilities, String.class);
    }

    @Named("capabilitiesPassThrough")
    default List<String> capabilitiesPassThrough(List<String> capabilities) {
        return capabilities;
    }

}
