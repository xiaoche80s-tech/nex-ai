package com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper;

import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Skill 版本 Mapper（不可变：只插不改不删）。
 */
@Mapper
public interface SkillVersionMapper extends BaseMapperX<SkillVersionDO> {

    /**
     * 按 skill 读取全部版本（版本号升序）
     */
    default List<SkillVersionDO> selectListBySkillId(Long skillId) {
        return selectList(new LambdaQueryWrapperX<SkillVersionDO>()
                .eq(SkillVersionDO::getSkillId, skillId)
                .orderByAsc(SkillVersionDO::getVersionNo));
    }

    /**
     * 读取最大版本号（登记新版本时计算）
     */
    default Integer selectMaxVersionNo(Long skillId) {
        SkillVersionDO latest = selectOne(new LambdaQueryWrapperX<SkillVersionDO>()
                .eq(SkillVersionDO::getSkillId, skillId)
                .orderByDesc(SkillVersionDO::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getVersionNo();
    }

}
