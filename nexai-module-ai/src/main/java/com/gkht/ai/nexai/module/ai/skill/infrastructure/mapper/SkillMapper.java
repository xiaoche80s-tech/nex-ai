package com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 技能 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface SkillMapper extends BaseMapperX<SkillDO> {

    default PageResult<SkillDO> selectPage(PageParam pageParam, String name) {
        return selectPage(pageParam, new LambdaQueryWrapperX<SkillDO>()
                .likeIfPresent(SkillDO::getName, name)
                .orderByDesc(SkillDO::getId));
    }

    default SkillDO selectByName(String name) {
        return selectOne(SkillDO::getName, name);
    }

    /**
     * 全部已发布技能（current_version_no 非空），运行时仓储读侧使用
     */
    default List<SkillDO> selectListPublished() {
        return selectList(new LambdaQueryWrapperX<SkillDO>()
                .isNotNull(SkillDO::getCurrentVersionNo)
                .orderByAsc(SkillDO::getName));
    }

}
