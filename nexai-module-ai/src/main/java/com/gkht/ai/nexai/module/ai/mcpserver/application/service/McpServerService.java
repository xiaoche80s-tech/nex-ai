package com.gkht.ai.nexai.module.ai.mcpserver.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerCreateCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerUpdateCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpProbeResultDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpServerDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.application.query.McpServerPageQuery;

/**
 * MCP Server 应用服务：注册/编辑/启停/删除 + 连通探测（拉取工具清单并回写缓存）。
 */
public interface McpServerService {

    /** 注册 MCP Server（TENANT 归属，BYO-MCP），返回编号 */
    Long createMcpServer(McpServerCreateCommand command);

    /** 更新接入配置（headers 为 null 时保留原认证头） */
    void updateMcpServer(McpServerUpdateCommand command);

    /** 启用/停用 */
    void updateMcpServerStatus(McpServerUpdateStatusCommand command);

    /** 删除 */
    void deleteMcpServer(Long id);

    /** 详情 */
    McpServerDTO getMcpServer(Long id);

    /** 分页 */
    PageResult<McpServerDTO> getMcpServerPage(McpServerPageQuery query);

    /** 连通探测 + 工具清单拉取；成功时把工具名清单回写 availableTools 缓存 */
    McpProbeResultDTO probeMcpServer(Long id);

}
