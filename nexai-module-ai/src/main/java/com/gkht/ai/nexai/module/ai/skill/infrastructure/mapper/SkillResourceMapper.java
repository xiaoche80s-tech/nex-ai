package com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper;

import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillResourceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Skill 版本资源 Mapper（不可变：只插不更新；删除仅在级联清理发生）。
 */
@Mapper
public interface SkillResourceMapper extends BaseMapperX<SkillResourceDO> {

    /**
     * 按版本读取全部资源行（路径升序，稳定装配顺序）
     */
    default List<SkillResourceDO> selectListByVersionId(Long versionId) {
        return selectList(new LambdaQueryWrapperX<SkillResourceDO>()
                .eq(SkillResourceDO::getVersionId, versionId)
                .orderByAsc(SkillResourceDO::getResourcePath));
    }

}
