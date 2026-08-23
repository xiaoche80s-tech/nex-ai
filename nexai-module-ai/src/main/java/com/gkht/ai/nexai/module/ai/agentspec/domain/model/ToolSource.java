package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

/**
 * 工具挂载来源（CONTEXT.md「Tool」词条）：智能体可调用工具的挂载通道。
 *
 * <ul>
 *   <li>{@link #MCP} —— 经注册的 MCP Server 提供（sourceId = ai_mcp_server.id）</li>
 *   <li>{@link #PLATFORM} —— 平台工具库（@Tool 注解注册的业务工具条目，sourceId 由平台工具库工单定义）</li>
 * </ul>
 *
 * <p>内置工具（文件六件套/Todo 等）不参与挂载——随执行环境层配置自动启用。</p>
 */
public enum ToolSource {

    /** MCP Server 提供的工具 */
    MCP,

    /** 平台工具库（@Tool 注册的业务工具） */
    PLATFORM

}
