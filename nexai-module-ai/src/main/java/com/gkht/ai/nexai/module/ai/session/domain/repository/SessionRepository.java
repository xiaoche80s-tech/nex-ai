package com.gkht.ai.nexai.module.ai.session.domain.repository;

import com.gkht.ai.nexai.module.ai.session.domain.model.Session;

/**
 * 会话聚合 Repository 端口（写模型侧）。
 * 分页查询属读侧，由应用服务经 Mapper 直查，不经此端口。
 */
public interface SessionRepository {

    /**
     * 保存聚合：id 为空时新增，否则更新；返回落库后的编号
     */
    Long save(Session session);

    /**
     * 按编号加载聚合，不存在返回 null
     */
    Session findById(Long id);

}
