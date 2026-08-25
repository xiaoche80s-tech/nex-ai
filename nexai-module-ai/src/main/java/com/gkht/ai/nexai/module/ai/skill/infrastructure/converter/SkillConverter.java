package com.gkht.ai.nexai.module.ai.skill.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import lombok.Data;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Map;

/**
 * Skill 转换器：领域模型 → DO（Repository 反方向在 RepositoryImpl 经 reconstitute）、
 * 领域模型/DO → 出参 DTO。能力包内容在 DO 侧为 JSON 字符串、领域侧为 {@link SkillContent}，
 * 互转集中于此（ContentJSON 桥接 POJO，domain 值对象零框架依赖）。
 */
@Mapper(componentModel = "spring")
public interface SkillConverter {

    @Mapping(target = "ownerLevel", source = "ownerLevel", qualifiedByName = "ownerLevelToString")
    @Mapping(target = "published", source = "published", qualifiedByName = "publishedToInt")
    SkillDO toDataObject(Skill skill);

    @Mapping(target = "content", source = "content", qualifiedByName = "contentToJson")
    SkillVersionDO toVersionDataObject(SkillVersion version);

    SkillDTO toDTO(SkillDO skillDO);

    List<SkillDTO> toDTOList(List<SkillDO> list);

    default PageResult<SkillDTO> toDTOPage(PageResult<SkillDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    /** 版本 DO → 版本 DTO（不含全量内容；current 由应用服务按版本指针设置） */
    SkillVersionDTO toVersionDTO(SkillVersionDO versionDO);

    List<SkillVersionDTO> toVersionDTOList(List<SkillVersionDO> list);

    /** 内容 JSON → 领域值对象（reconstitute 用） */
    default SkillContent jsonToContent(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        ContentJSON parsed = JsonUtils.parseObject(json, ContentJSON.class);
        return parsed == null ? null : parsed.toDomain();
    }

    @Named("contentToJson")
    default String contentToJson(SkillContent content) {
        return content == null ? null : JsonUtils.toJsonString(ContentJSON.from(content));
    }

    @Named("ownerLevelToString")
    default String ownerLevelToString(SkillOwnerLevel level) {
        return level == null ? null : level.name();
    }

    /** 上架位 boolean → Integer（0=下架 1=上架，DDL int4） */
    @Named("publishedToInt")
    default Integer publishedToInt(boolean published) {
        return published ? 1 : 0;
    }

    /**
     * JSON 编解码桥接 POJO：能力包内容（markdown + resources Map）。
     */
    @Data
    class ContentJSON {

        private String markdown;
        private Map<String, String> resources;

        static ContentJSON from(SkillContent content) {
            ContentJSON json = new ContentJSON();
            json.setMarkdown(content.getMarkdown());
            json.setResources(content.resourcesCopy());
            return json;
        }

        SkillContent toDomain() {
            return SkillContent.of(markdown, resources);
        }
    }

}
