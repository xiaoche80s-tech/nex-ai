package com.gkht.ai.nexai.module.ai.session.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 会话 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface SessionMapper extends BaseMapperX<SessionDO> {

    /**
     * 分页查询（MVP 调试会话列表，可按规格与状态过滤）
     */
    default PageResult<SessionDO> selectPage(PageParam pageParam, Long specId, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<SessionDO>()
                .eq(specId != null, SessionDO::getSpecId, specId)
                .eq(status != null && !status.isBlank(), SessionDO::getStatus, status)
                .orderByDesc(SessionDO::getId));
    }

    /**
     * 按会话业务键读取（恢复链路按 sessionKey 寻址；租户过滤由租户插件完成）
     */
    default SessionDO selectBySessionKey(String sessionKey) {
        return selectOne(new LambdaQueryWrapperX<SessionDO>()
                .eq(SessionDO::getSessionKey, sessionKey));
    }

    /**
     * 租户下会话列表（按创建时间倒序）
     */
    default List<SessionDO> selectListByTenant(String type, Long specId, int limit) {
        return selectList(new LambdaQueryWrapperX<SessionDO>()
                .eq(SessionDO::getType, type)
                .eq(specId != null, SessionDO::getSpecId, specId)
                .orderByDesc(SessionDO::getId)
                .last("LIMIT " + limit));
    }

}
