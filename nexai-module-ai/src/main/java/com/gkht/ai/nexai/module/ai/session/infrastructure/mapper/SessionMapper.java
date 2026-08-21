package com.gkht.ai.nexai.module.ai.session.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface SessionMapper extends BaseMapperX<SessionDO> {

    default PageResult<SessionDO> selectPage(PageParam pageParam, Integer type, Long specId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<SessionDO>()
                .eqIfPresent(SessionDO::getType, type)
                .eqIfPresent(SessionDO::getSpecId, specId)
                .orderByDesc(SessionDO::getId));
    }

}
