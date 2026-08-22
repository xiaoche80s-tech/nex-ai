package com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper;

import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillContentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 技能内容 Mapper：内容行由 RepositoryImpl 管理生命周期（插入新行 / 无引用清理 / 聚合级联删），
 * 只插入与查询，不提供内容更新（内容行不可变）。
 */
@Mapper
public interface SkillContentMapper extends BaseMapperX<SkillContentDO> {

    default List<SkillContentDO> selectListByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<SkillContentDO>()
                .in(SkillContentDO::getId, ids));
    }

    /**
     * 某技能的全部内容行（含版本引用与历史草稿行，聚合级联删除用）
     */
    default List<SkillContentDO> selectListBySkillId(Long skillId) {
        return selectList(new LambdaQueryWrapperX<SkillContentDO>()
                .eq(SkillContentDO::getSkillId, skillId));
    }

    default void deleteBySkillId(Long skillId) {
        delete(SkillContentDO::getSkillId, skillId);
    }

}
