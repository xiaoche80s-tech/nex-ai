package com.gkht.ai.nexai.module.ai.skill.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillResourceDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 转换器：领域模型 → DO（Repository 反方向在 RepositoryImpl 经 reconstitute）、
 * 领域模型/DO → 出参 DTO。版本内容已拆表（工单 26）：markdown 走 skill_markdown 列、
 * 资源走 ai_skill_resources 行（本类提供 Map ↔ 行的双向转换），ContentJSON 桥接已移除。
 */
@Mapper(componentModel = "spring")
public interface SkillConverter {

    @Mapping(target = "ownerLevel", source = "ownerLevel", qualifiedByName = "ownerLevelToString")
    @Mapping(target = "published", source = "published", qualifiedByName = "publishedToInt")
    SkillDO toDataObject(Skill skill);

    @Mapping(target = "skillMarkdown", source = "content.markdown")
    SkillVersionDO toVersionDataObject(SkillVersion version);

    SkillDTO toDTO(SkillDO skillDO);

    List<SkillDTO> toDTOList(List<SkillDO> list);

    default PageResult<SkillDTO> toDTOPage(PageResult<SkillDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    /** 版本 DO → 版本 DTO（不含全量内容；current 由应用服务按版本指针设置） */
    SkillVersionDTO toVersionDTO(SkillVersionDO versionDO);

    List<SkillVersionDTO> toVersionDTOList(List<SkillVersionDO> list);

    /** 版本资源 Map → 资源行（versionId 为版本落库后的 DB 主键） */
    default List<SkillResourceDO> contentToResourceDOs(Long versionId, SkillContent content) {
        if (content == null || content.getResources().isEmpty()) {
            return List.of();
        }
        return content.getResources().entrySet().stream()
                .map(entry -> {
                    SkillResourceDO row = new SkillResourceDO();
                    row.setVersionId(versionId);
                    row.setResourcePath(entry.getKey());
                    row.setResourceContent(entry.getValue());
                    return row;
                })
                .toList();
    }

    /** 资源行 + markdown → 领域值对象（reconstitute 用） */
    default SkillContent toContent(String markdown, List<SkillResourceDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return SkillContent.of(markdown, null);
        }
        Map<String, String> resources = new LinkedHashMap<>(rows.size());
        rows.forEach(row -> resources.put(row.getResourcePath(), row.getResourceContent()));
        return SkillContent.of(markdown, resources);
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

}
