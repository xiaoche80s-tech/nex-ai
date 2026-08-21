package com.gkht.ai.nexai.module.ai.model.domain.repository;

import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;

/**
 * 渠道聚合的 Repository 端口（写模型侧）。
 * 分页查询属读侧，由应用服务经 Mapper 直查，不经此端口。
 */
public interface ChannelRepository {

    /**
     * 保存聚合：id 为空时新增，否则更新；返回落库后的编号
     */
    Long save(Channel channel);

    /**
     * 按编号加载聚合，不存在返回 null
     */
    Channel findById(Long id);

    /**
     * 按编号删除聚合（逻辑删除）
     */
    void deleteById(Long id);

}
