package com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Skill Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface SkillMapper extends BaseMapperX<SkillDO> {

    /**
     * 分页查询（MVP 可见性口径：租户级租户内全见 + 用户级仅归属用户可见，与规格一致）
     */
    default PageResult<SkillDO> selectPage(PageParam pageParam, String name, Long currentUserId) {
        LambdaQueryWrapperX<SkillDO> query = new LambdaQueryWrapperX<SkillDO>()
                .likeIfPresent(SkillDO::getName, name)
                .orderByDesc(SkillDO::getId);
        String userLevel = SkillOwnerLevel.USER.name();
        if (currentUserId == null) {
            query.ne(SkillDO::getOwnerLevel, userLevel);
        } else {
            query.and(wrapper -> wrapper.ne(SkillDO::getOwnerLevel, userLevel)
                    .or().eq(SkillDO::getOwnerUserId, currentUserId));
        }
        return selectPage(pageParam, query);
    }

    /**
     * 同归属下名称是否已存在（创建时唯一性预校验）
     */
    default boolean existsByNameAndOwner(SkillOwnerLevel ownerLevel, Long ownerUserId, String name) {
        return selectCount(new LambdaQueryWrapperX<SkillDO>()
                .eq(SkillDO::getOwnerLevel, ownerLevel.name())
                .eq(ownerUserId != null, SkillDO::getOwnerUserId, ownerUserId)
                .isNull(ownerUserId == null, SkillDO::getOwnerUserId)
                .eq(SkillDO::getName, name)) > 0;
    }

}
