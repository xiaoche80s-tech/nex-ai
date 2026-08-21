package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper;

import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 智能体规格版本 Mapper：只插入与查询，不提供更新（已发布版本不可变）。
 */
@Mapper
public interface AgentSpecVersionMapper extends BaseMapperX<AgentSpecVersionDO> {

    default List<AgentSpecVersionDO> selectListBySpecId(Long specId) {
        return selectList(new LambdaQueryWrapperX<AgentSpecVersionDO>()
                .eq(AgentSpecVersionDO::getSpecId, specId)
                .orderByDesc(AgentSpecVersionDO::getVersionNo));
    }

    /**
     * 按规格 + 版本号精确定位一个版本（会话装配定位不可变快照用）
     */
    default AgentSpecVersionDO selectBySpecIdAndVersionNo(Long specId, Integer versionNo) {
        return selectOne(new LambdaQueryWrapperX<AgentSpecVersionDO>()
                .eq(AgentSpecVersionDO::getSpecId, specId)
                .eq(AgentSpecVersionDO::getVersionNo, versionNo));
    }

    /**
     * 按规格编号集合批量查询（列表页补充默认版本信息用）
     */
    default List<AgentSpecVersionDO> selectListBySpecIds(Collection<Long> specIds) {
        if (specIds == null || specIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<AgentSpecVersionDO>()
                .in(AgentSpecVersionDO::getSpecId, specIds)
                .orderByDesc(AgentSpecVersionDO::getVersionNo));
    }

    default void deleteBySpecId(Long specId) {
        delete(AgentSpecVersionDO::getSpecId, specId);
    }

}
