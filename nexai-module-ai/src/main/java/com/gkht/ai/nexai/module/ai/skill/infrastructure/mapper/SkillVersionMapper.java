package com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper;

import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 技能版本 Mapper：只插入与查询，不提供内容更新（已发布版本不可变）；
 * deleteBySkillId 例外——随技能聚合整体逻辑删除（级联删除不是内容变更）。
 */
@Mapper
public interface SkillVersionMapper extends BaseMapperX<SkillVersionDO> {

    default List<SkillVersionDO> selectListBySkillId(Long skillId) {
        return selectList(new LambdaQueryWrapperX<SkillVersionDO>()
                .eq(SkillVersionDO::getSkillId, skillId)
                .orderByDesc(SkillVersionDO::getVersionNo));
    }

    default SkillVersionDO selectBySkillIdAndVersionNo(Long skillId, Integer versionNo) {
        return selectOne(new LambdaQueryWrapperX<SkillVersionDO>()
                .eq(SkillVersionDO::getSkillId, skillId)
                .eq(SkillVersionDO::getVersionNo, versionNo));
    }

    default void deleteBySkillId(Long skillId) {
        delete(SkillVersionDO::getSkillId, skillId);
    }

    /**
     * 按技能编号集合批量查询（运行时仓储批量组装已发布技能用）
     */
    default List<SkillVersionDO> selectListBySkillIds(Collection<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<SkillVersionDO>()
                .in(SkillVersionDO::getSkillId, skillIds)
                .orderByDesc(SkillVersionDO::getVersionNo));
    }

}
