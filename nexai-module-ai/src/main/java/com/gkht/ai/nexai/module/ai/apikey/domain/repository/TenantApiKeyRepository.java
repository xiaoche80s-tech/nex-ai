package com.gkht.ai.nexai.module.ai.apikey.domain.repository;

import com.gkht.ai.nexai.module.ai.apikey.domain.model.TenantApiKey;

/**
 * 租户 API Key 仓储端口（一个聚合一个，按聚合不按表）。分页查询走应用层
 * 轻量读写分离（经 Mapper 直查转 DTO，对齐 Session 模式），不经本端口。
 */
public interface TenantApiKeyRepository {

    /** 保存（新建回填编号） */
    Long save(TenantApiKey key);

    /** 更新（吊销） */
    void update(TenantApiKey key);

    /** 按编号读（租户隔离由租户插件保证） */
    TenantApiKey findById(Long id);

    /** 按哈希寻址（认证过滤器校验路径；租户上下文缺失时跨租户寻址——校验即鉴权，显式绕过租户插件） */
    TenantApiKey findByKeyHash(String keyHash);

}
