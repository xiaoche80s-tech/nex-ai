package com.gkht.ai.nexai.module.ai.skill.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.skill.domain.valueobject.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverter;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillContentDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillResourceDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillContentMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillResourceMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能内容装配器（infrastructure 内部组件，非领域概念）：内容行编号 → 完整技能内容
 * （内容行 + 资源行批量组配）。聚合 Repository 与官方仓储实现的读侧共用，
 * 保证「SKILL.md 原文 + 行级资源 → SkillContent 值对象」只有一种装配方式。
 */
@Component
public class SkillContentAssembler {

    @Resource
    private SkillContentMapper skillContentMapper;

    @Resource
    private SkillResourceMapper skillResourceMapper;

    @Resource
    private SkillConverter skillConverter;

    /**
     * 批量装配：内容行 → 资源行一次批量查询后内存分组；空入参返回空 Map，缺失的编号不出现在结果里
     */
    public Map<Long, SkillContent> assemble(Collection<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Map.of();
        }
        List<SkillContentDO> contents = skillContentMapper.selectListByIds(contentIds);
        if (contents.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SkillResourceDO>> resourcesByContentId = skillResourceMapper
                .selectListByContentIds(contents.stream().map(SkillContentDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(SkillResourceDO::getContentId));
        return contents.stream().collect(Collectors.toMap(SkillContentDO::getId,
                content -> SkillContent.of(content.getSkillMd(),
                        skillConverter.resourcesToMap(resourcesByContentId.get(content.getId()))),
                (a, b) -> a));
    }

    /** 单份装配：内容行缺失返回 null */
    public SkillContent assembleOne(Long contentId) {
        if (contentId == null) {
            return null;
        }
        return assemble(List.of(contentId)).get(contentId);
    }

}
