package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolMount;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;

import java.util.List;

/**
 * 智能体运行时装配指令值对象（不可变）：描述一次会话调用所需的全部运行时信息——
 * 会话槽位（userId/sessionKey/tenantId/agentId）、配置来源（规格/渠道/模型）、执行环境与
 * 调用参数。由应用层从持久化数据组装，基础设施层消费。
 *
 * <p>快路径/回退分流（工单 08）：常驻实例按 {@code specReference()}（规格 + 版本号）缓存；
 * 版本戳 = 渠道/模型/MCP Server 更新时间 + 技能挂载指纹 + specReference，任一变化即失效
 * 重建（新会话即用新配置）。命中缓存的调用复用常驻实例（快路径）；版本号不同自然回落
 * per-spec 装配（回退）。</p>
 *
 * <p>挂载解析（工单 12/13）：应用层把挂载引用解析为「装配就绪」形态传入——技能经
 * {@link SkillMountDirectory}（幂等物化后的基目录 + 名单 + 版本指纹），MCP Server 经
 * {@link McpServer} 聚合本体（连接配置直接可用，对齐 Channel/Model 模式）；平台工具库
 * 条目（source=PLATFORM）由基础设施层经注册表按 sourceId 寻址（代码内声明，无 DB）。</p>
 */
public final class AgentRuntimeConfig {

    /** 会话定位：userId（agentscope 槽位），匿名时固定 anonymous */
    private final String userId;
    /** 会话定位：sessionKey（agentscope 槽位），即平台会话业务键 */
    private final String sessionKey;
    /** 租户编号（workspace 布局 t{tenantId} 与 RuntimeContext extra.tenantId 来源，应用层从租户上下文注入） */
    private final Long tenantId;
    /** agentId（workspace 目录与状态槽位名），= specCode，稳定可预测 */
    private final String agentId;
    /** 装配 agentName（日志与 workspace 展示），= {specCode}-v{versionNo} */
    private final String agentName;

    /** 配置来源：规格编号（装配时读当前版本快照） */
    private final Long specId;
    /** 配置来源：版本号（装配时读该版本快照，null = 当前版本） */
    private final Integer versionNo;
    /** 归属层级与归属用户（workspace 布局用） */
    private final OwnerLevel ownerLevel;
    private final Long ownerUserId;

    /** 行为配置：系统提示（物化为 AGENTS.md）、迭代上限、调用参数 */
    private final String systemPrompt;
    private final Integer maxIters;
    private final GenerateOptions generateOptions;

    /** 执行环境层 */
    private final ExecutionEnvConfig executionEnv;

    /** 挂载层：工具挂载列表（MCP/平台工具库，含敏感名单 → permission ASK 翻译源） */
    private final List<ToolMount> tools;

    /** 挂载层：技能挂载目录（物化基目录 + 名单 + 版本指纹），空 = 无技能挂载 */
    private final List<SkillMountDirectory> skillMounts;

    /** 挂载层：MCP Server 聚合本体（source=MCP 挂载的解析结果，连接配置直接可用），空 = 无 MCP 挂载 */
    private final List<McpServer> mcpServers;

    /** 挂载层：规格私有文件夹（ASSET/TOOLSET，装配期物化进 workspace，工单 18），空 = 无文件夹挂载 */
    private final List<FolderMount> folders;

    /** 模型来源：渠道（端点/密钥/提供商）与模型标识 */
    private final Channel channel;
    private final Model model;

    private AgentRuntimeConfig(String userId, String sessionKey, Long tenantId, String agentId,
                               String agentName, Long specId, Integer versionNo,
                               OwnerLevel ownerLevel, Long ownerUserId, String systemPrompt,
                               Integer maxIters, GenerateOptions generateOptions,
                               ExecutionEnvConfig executionEnv, List<ToolMount> tools,
                               List<SkillMountDirectory> skillMounts, List<McpServer> mcpServers,
                               List<FolderMount> folders, Channel channel, Model model) {
        this.userId = userId;
        this.sessionKey = sessionKey;
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.agentName = agentName;
        this.specId = specId;
        this.versionNo = versionNo;
        this.ownerLevel = ownerLevel;
        this.ownerUserId = ownerUserId;
        this.systemPrompt = systemPrompt;
        this.maxIters = maxIters;
        this.generateOptions = generateOptions;
        this.executionEnv = executionEnv;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.skillMounts = skillMounts == null ? List.of() : List.copyOf(skillMounts);
        this.mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        this.folders = folders == null ? List.of() : List.copyOf(folders);
        this.channel = channel;
        this.model = model;
    }

    /**
     * 构建装配指令
     *
     * @param userId      会话用户（anonymous 亦可），不能为 null
     * @param sessionKey  会话业务键，不能为 null
     * @param agentId     = specCode，不能为 null
     * @param agentName   装配名，不能为 null
     * @param specId      规格编号，不能为 null
     * @param versionNo   版本号，null = 当前版本
     * @param ownerLevel  归属层级，不能为 null
     * @param ownerUserId 归属用户（用户级），可空
     * @param systemPrompt 系统提示，可空
     * @param maxIters    迭代上限，可空
     * @param generateOptions 调用参数，可空
     * @param executionEnv 执行环境，可空 = 全关
     * @param tools       工具挂载列表，可空
     * @param skillMounts 技能挂载目录列表，可空
     * @param mcpServers  MCP Server 聚合列表（source=MCP 挂载解析结果），可空
     * @param folders     规格私有文件夹挂载列表（装配期物化进 workspace），可空
     * @param channel     渠道，不能为 null
     * @param model       模型，不能为 null
     */
    public static AgentRuntimeConfig of(String userId, String sessionKey, Long tenantId,
                                        String agentId, String agentName, Long specId,
                                        Integer versionNo, OwnerLevel ownerLevel,
                                        Long ownerUserId, String systemPrompt, Integer maxIters,
                                        GenerateOptions generateOptions,
                                        ExecutionEnvConfig executionEnv,
                                        List<ToolMount> tools,
                                        List<SkillMountDirectory> skillMounts,
                                        List<McpServer> mcpServers,
                                        List<FolderMount> folders,
                                        Channel channel, Model model) {
        if (userId == null || sessionKey == null || tenantId == null || agentId == null
                || agentName == null || specId == null || ownerLevel == null
                || channel == null || model == null) {
            throw new IllegalArgumentException("装配指令的必填字段不能为空");
        }
        return new AgentRuntimeConfig(userId, sessionKey, tenantId, agentId, agentName, specId,
                versionNo, ownerLevel, ownerUserId, systemPrompt, maxIters, generateOptions,
                executionEnv, tools, skillMounts, mcpServers, folders, channel, model);
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    /** 租户编号（workspace 布局与 RuntimeContext extra.tenantId 来源） */
    public Long getTenantId() {
        return tenantId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public Long getSpecId() {
        return specId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public OwnerLevel getOwnerLevel() {
        return ownerLevel;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Integer getMaxIters() {
        return maxIters;
    }

    public GenerateOptions getGenerateOptions() {
        return generateOptions;
    }

    /** 执行环境配置，null = 全关（纯对话智能体） */
    public ExecutionEnvConfig getExecutionEnv() {
        return executionEnv;
    }

    /** 工具挂载列表（含敏感名单），空 = 无挂载 */
    public List<ToolMount> getTools() {
        return tools;
    }

    /** 技能挂载目录列表，空 = 无技能挂载 */
    public List<SkillMountDirectory> getSkillMounts() {
        return skillMounts;
    }

    /** MCP Server 聚合列表（source=MCP 挂载解析结果），空 = 无 MCP 挂载 */
    public List<McpServer> getMcpServers() {
        return mcpServers;
    }

    /** 规格私有文件夹挂载列表（装配期物化进 workspace），空 = 无文件夹挂载 */
    public List<FolderMount> getFolders() {
        return folders;
    }

    public Channel getChannel() {
        return channel;
    }

    public Model getModel() {
        return model;
    }

    /** 规格引用签名：specId + 版本号（null 视为 0），构成常驻缓存键一部分 */
    public String specReference() {
        return specId + ":" + (versionNo == null ? 0 : versionNo);
    }
}
