package com.gkht.ai.nexai.module.ai.session.domain.repository;

import com.gkht.ai.nexai.module.ai.session.domain.model.Session;
import com.gkht.ai.nexai.module.ai.session.domain.model.SessionType;

import java.util.List;

/**
 * 会话聚合仓储端口（按聚合不按表）：会话元数据（标题/绑定规格版本/状态/消息轮次/HITL 挂起上下文）的
 * 持久化。会话的智能体状态上下文不落本聚合——由 agentscope PostgresAgentStateStore 按
 * (userId, sessionId) 槽位管理，本端口只管平台侧会话记录。
 *
 * <p>列表查询走轻量读写分离（应用服务经 Mapper 直查转 DTO），不经本端口。</p>
 */
public interface SessionRepository {

    /**
     * 保存会话：无编号时插入（回填编号）
     *
     * @return 会话编号
     */
    Long save(Session session);

    /**
     * 按编号读取会话（含消息轮次与 HITL 挂起上下文；跨租户/已删除返回 null）
     */
    Session findById(Long id);

    /**
     * 按会话业务键读取会话（恢复链路按 sessionKey 寻址）
     */
    Session findBySessionKey(String sessionKey);

    /**
     * 更新会话（标题/状态/轮次/挂起上下文的落库）
     */
    void update(Session session);

    /**
     * 读取租户下的会话列表（按创建时间倒序，MVP 调试会话）
     */
    List<Session> listByTenant(SessionType type, Long specId, int limit);

}
