package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import java.util.List;

/**
 * 规格草稿命令公共契约：创建与编辑命令共有的主体信息 + 草稿配置字段。
 * 供应用服务统一转换为领域配置值对象，避免两套 command 重复转换逻辑。
 */
public interface AgentSpecDraftCommand {

    String getName();

    String getDescription();

    String getIcon();

    Long getModelId();

    String getSystemPrompt();

    Integer getMaxIters();

    Double getTemperature();

    List<Long> getSkillIds();

    List<Long> getKnowledgeBaseIds();

    List<Long> getMcpServerIds();

    List<Long> getSubagentSpecIds();

}
