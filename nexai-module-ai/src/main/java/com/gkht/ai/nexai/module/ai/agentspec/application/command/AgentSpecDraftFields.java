package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.ToolMountCommand;

import java.util.List;

/**
 * 规格草稿配置平铺字段公共读面（创建/更新命令共同实现）：
 * 应用服务据此组装四层配置值对象，平铺 → 分层的映射逻辑不在两个命令上重复。
 */
public interface AgentSpecDraftFields {

    Long getModelId();

    String getDescription();

    String getSystemPrompt();

    Integer getMaxIters();

    Double getTemperature();

    Double getTopP();

    Integer getMaxTokens();

    List<Long> getSkillIds();

    List<ToolMountCommand> getTools();

    List<FolderMountCommand> getFolders();

    Boolean getWorkspaceEnabled();

    Boolean getSandboxEnabled();

    List<String> getCapabilities();

}
