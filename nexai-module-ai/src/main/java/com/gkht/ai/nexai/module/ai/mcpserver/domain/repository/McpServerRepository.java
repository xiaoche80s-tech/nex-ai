package com.gkht.ai.nexai.module.ai.mcpserver.domain.repository;

import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;

/**
 * MCP Server 聚合仓储端口（按聚合不按表）。列表查询走轻量读写分离
 * （应用服务经 Mapper 直查转 DTO）。
 */
public interface McpServerRepository {

    /**
     * 保存 MCP Server：无编号时插入（回填编号），有编号时更新
     * （探测清单回写等主体变更也走此方法）
     *
     * @return MCP Server 编号
     */
    Long save(McpServer server);

    /**
     * 按编号读取 MCP Server，不存在返回 null（跨租户/已删除均归此）
     */
    McpServer findById(Long id);

    /**
     * 按编号删除
     */
    void deleteById(Long id);

}
