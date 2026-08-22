package com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper;

import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillResourceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 技能资源文件 Mapper：随所属内容行整体存取与清理，单行不提供更新
 * （内容行不可变，改资源 = 编辑草稿写新内容行）。
 */
@Mapper
public interface SkillResourceMapper extends BaseMapperX<SkillResourceDO> {

    default List<SkillResourceDO> selectListByContentIds(Collection<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<SkillResourceDO>()
                .in(SkillResourceDO::getContentId, contentIds)
                .orderByAsc(SkillResourceDO::getPath));
    }

    default void deleteByContentIds(Collection<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return;
        }
        delete(new LambdaQueryWrapperX<SkillResourceDO>()
                .in(SkillResourceDO::getContentId, contentIds));
    }

}
