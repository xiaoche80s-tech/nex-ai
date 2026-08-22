package com.gkht.ai.nexai.module.ai.skill.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillContentDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDetailDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillResourceDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能转换器：领域模型 → 主表/版本表 DO、领域模型 → 出参 DTO。
 * 内容与资源不在主表/版本表列中（ADR-0003 修订：内容独立成表 + 资源行级子表），
 * 跨表装配在 RepositoryImpl / AgentscopeSkillRepository 完成，converter 只做同构字段映射。
 */
@Mapper(componentModel = "spring")
public interface SkillConverter {

    /**
     * 聚合根 → 主表 DO。draftContentId 忽略：内容行先落库拿编号、再回填指针，由 RepositoryImpl 编排
     */
    @Mapping(target = "draftContentId", ignore = true)
    SkillDO toDataObject(Skill skill);

    /**
     * 版本实体 → 版本表 DO（纯指针行）。contentId 忽略：发布引用转正时由 RepositoryImpl 从主表草稿指针补
     */
    @Mapping(target = "contentId", ignore = true)
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
            rows.get(i).setHasDraft(page.getList().get(i).getDraftContentId() != null);
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
     * 资源行集合 → path → content 有序 Map（保持 path 升序的稳定装配形态）
     */
    default Map<String, String> resourcesToMap(List<SkillResourceDO> resources) {
        Map<String, String> map = new LinkedHashMap<>();
        if (resources != null) {
            for (SkillResourceDO resource : resources) {
                map.put(resource.getPath(), resource.getContent());
            }
        }
        return map;
    }

}
