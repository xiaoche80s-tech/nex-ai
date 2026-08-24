package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 智能体规格版本快照 Mapper。快照不可变，只有插入与查询。
 * 版本号由应用服务在事务内（max + 1）计算，DB 唯一索引兜底并发。
 */
@Mapper
public interface AgentSpecVersionMapper extends BaseMapperX<AgentSpecVersionDO> {

    /** 规格下最大版本号，从未发布返回 null（subquery 实现，H2/PG 方言通用） */
    default Integer selectMaxVersionNo(Long specId) {
        AgentSpecVersionDO last = selectOne(new LambdaQueryWrapper<AgentSpecVersionDO>()
                .select(AgentSpecVersionDO::getVersionNo)
                .eq(AgentSpecVersionDO::getSpecId, specId)
                .orderByDesc(AgentSpecVersionDO::getVersionNo)
                .last("LIMIT 1"));
        return last == null ? null : last.getVersionNo();
    }

    /** 规格下全部版本快照（版本号升序，供版本列表与快照全量比对） */
    default List<AgentSpecVersionDO> selectListBySpecId(Long specId) {
        return selectList(new LambdaQueryWrapper<AgentSpecVersionDO>()
                .eq(AgentSpecVersionDO::getSpecId, specId)
                .orderByAsc(AgentSpecVersionDO::getVersionNo));
    }

    /** 规格下某版本号是否存在（切换当前版本前的存在性校验） */
    default boolean existsBySpecAndVersion(Long specId, int versionNo) {
        return selectCount(new LambdaQueryWrapper<AgentSpecVersionDO>()
                .eq(AgentSpecVersionDO::getSpecId, specId)
                .eq(AgentSpecVersionDO::getVersionNo, versionNo)) > 0;
    }

    /** 按规格 + 版本号取单条快照（按需读取——config 为全量 JSON，不随列表多拉），不存在返回 null */
    default AgentSpecVersionDO selectBySpecIdAndVersionNo(Long specId, int versionNo) {
        return selectOne(new LambdaQueryWrapper<AgentSpecVersionDO>()
                .eq(AgentSpecVersionDO::getSpecId, specId)
                .eq(AgentSpecVersionDO::getVersionNo, versionNo));
    }

}
