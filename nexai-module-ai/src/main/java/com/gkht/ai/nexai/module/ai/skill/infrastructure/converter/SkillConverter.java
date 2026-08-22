package com.gkht.ai.nexai.module.ai.skill.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillContentDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDetailDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能转换器：领域模型 → DO、领域模型 → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 reconstitute 完成。
 * 资源文件集在 DO 侧为 JSON 字符串、领域侧为 {@link SkillContent} 内的不可变 Map，互转集中于此。
 */
@Mapper(componentModel = "spring")
public interface SkillConverter {

    @Mapping(target = "draftSkillMd", source = "draft", qualifiedByName = "draftToSkillMd")
    @Mapping(target = "draftResources", source = "draft", qualifiedByName = "draftToResourcesJson")
    SkillDO toDataObject(Skill skill);

    @Mapping(target = "skillMd", source = "content", qualifiedByName = "contentToSkillMd")
    @Mapping(target = "resources", source = "content", qualifiedByName = "contentToResourcesJson")
    SkillVersionDO toVersionDataObject(SkillVersion version);

    /**
     * hasDraft 显式忽略：MapStruct 对可空 source 的条件包裹使「无草稿（null）」无法与「false」区分，
     * 统一在 toDTOPage / 服务层显式置位
     */
    @Mapping(target = "hasDraft", ignore = true)
    SkillDTO toDTO(SkillDO skillDO);

    List<SkillDTO> toDTOList(List<SkillDO> list);

    default PageResult<SkillDTO> toDTOPage(PageResult<SkillDO> page) {
        List<SkillDTO> rows = toDTOList(page.getList());
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setHasDraft(draftMdToPresent(page.getList().get(i).getDraftSkillMd()));
        }
        return new PageResult<>(rows, page.getTotal());
    }

    /**
     * 聚合根 → 详情出参：草稿 / 当前版本快照 / hasDraft 由服务层补充
     */
    @Mapping(target = "hasDraft", ignore = true)
    @Mapping(target = "draft", ignore = true)
    @Mapping(target = "currentVersion", ignore = true)
    SkillDetailDTO toDetailDTO(Skill skill);

    @Mapping(target = "content", source = "content")
    SkillVersionDTO toVersionDTO(SkillVersion version);

    SkillContentDTO toContentDTO(SkillContent content);

    /**
     * 草稿值对象 → SKILL.md 原文列；null 草稿保持 null（表示无草稿）
     */
    @Named("draftToSkillMd")
    default String draftToSkillMd(SkillContent draft) {
        return draft == null ? null : draft.getSkillMd();
    }

    /**
     * 草稿值对象 → 资源 JSON 列；null 草稿保持 null（表示无草稿）
     */
    @Named("draftToResourcesJson")
    default String draftToResourcesJson(SkillContent draft) {
        return draft == null ? null : resourcesToJson(draft.getResources());
    }

    @Named("contentToSkillMd")
    default String contentToSkillMd(SkillContent content) {
        return content.getSkillMd();
    }

    @Named("contentToResourcesJson")
    default String contentToResourcesJson(SkillContent content) {
        return resourcesToJson(content.getResources());
    }

    /** 草稿 SKILL.md 列是否存在 → hasDraft 布尔（DO 侧） */
    @Named("draftMdToPresent")
    default Boolean draftMdToPresent(String draftSkillMd) {
        return draftSkillMd != null && !draftSkillMd.isBlank();
    }

    /**
     * DO 两列草稿 → 领域草稿值对象；两列均空返回 null（表示无草稿）
     */
    default SkillContent jsonToDraft(String draftSkillMd, String draftResources) {
        if (draftSkillMd == null || draftSkillMd.isBlank()) {
            return null;
        }
        return SkillContent.of(draftSkillMd, jsonToResources(draftResources));
    }

    /**
     * 资源文件集 JSON → Map；null / 空白 / 非法 JSON 均按空集处理（缺列容忍）
     */
    default Map<String, String> jsonToResources(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> parsed = JsonUtils.parseObjectQuietly(json,
                new tools.jackson.core.type.TypeReference<LinkedHashMap<String, String>>() {
                });
        return parsed != null ? parsed : Map.of();
    }

    default String resourcesToJson(Map<String, String> resources) {
        return JsonUtils.toJsonString(resources == null ? Map.of() : resources);
    }

}
