package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import java.util.List;

/**
 * 规格草稿命令公共契约：创建与编辑命令共有的主体信息 + 配置字段。
 * 配置字段按 agentscope 分层平铺（与表单字段一一对应），由应用服务组装为
 * 「三层 + 执行环境层」AgentSpecConfig（agent 层 / 模型调用层 / 挂载层 / 执行环境层）；
 * MCP 与子智能体挂载为结构化列表。
 */
public interface AgentSpecDraftCommand {

    String getName();

    String getIcon();

    Long getModelId();

    String getDescription();

    String getSystemPrompt();

    Integer getMaxIters();

    Double getTemperature();

    Double getTopP();

    Integer getMaxTokens();

    List<Long> getSkillIds();

    List<McpServerMountCommand> getMcpServers();

    List<SubagentMountCommand> getSubagents();

    Boolean getWorkspaceEnabled();

    Boolean getSandboxEnabled();

    List<String> getCapabilities();

}
