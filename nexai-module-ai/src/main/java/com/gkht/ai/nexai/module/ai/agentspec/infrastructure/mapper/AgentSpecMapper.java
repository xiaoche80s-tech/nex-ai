package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体规格 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface AgentSpecMapper extends BaseMapperX<AgentSpecDO> {

    default PageResult<AgentSpecDO> selectPage(PageParam pageParam, String name) {
        return selectPage(pageParam, new LambdaQueryWrapperX<AgentSpecDO>()
                .likeIfPresent(AgentSpecDO::getName, name)
                .orderByDesc(AgentSpecDO::getId));
    }

}
