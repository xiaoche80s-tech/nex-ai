package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.model.Model;

import java.util.Objects;

/**
 * 运行时装配指令值对象（不可变）：把「渠道 + 模型 + 规格版本快照 + 会话寻址 + 执行环境」
 * 一次性交给运行时端口。字段直接复用 model/agentspec 聚合的领域类型——这里是一次性调用的
 * 瞬时传值（跨聚合只读），不是聚合间的持久化引用（持久化仍只引用聚合根 ID），
 * 使端口契约不含 agentscope 类型（ADR-0001），也不退化为一堆松散 String。
 *
 * <p>调用参数以 {@link GenerateOptions} 整组传递（与 agentscope 分层对齐：
 * temperature/topP/maxTokens 属模型调用层）；会话级温度覆盖已在应用层合成进本组。</p>
 *
 * <p>执行环境与归属（ADR-0007）：{@code executionEnv} 决定 workspace 落盘与沙箱装配形态；
 * {@code specCode}/{@code ownerLevel}/{@code ownerUserId} 决定 workspace 目录布局与隔离 scope
 * （归属谁挂谁树下）；agentName 形如 {@code {spec_code}-v{versionNo}}。</p>
 */
public final class AgentRuntimeConfig {

    /** 会话标识（agentscope 状态存储的 sessionId） */
    private final String sessionKey;
    /** 运行时用户标识（agentscope 状态存储的 userId 槽位，取登录用户编号；平台级规格为复合 id） */
    private final String userId;
    /** 智能体名（agentscope agent name，用于追踪与日志） */
    private final String agentName;
    /** 业务编码（workspace 目录段来源，创建后不可变） */
    private final String specCode;
    /** 规格归属层级（决定 workspace 布局与 IsolationScope 取值） */
    private final OwnerLevel ownerLevel;
    /** 规格归属用户编号（用户级规格的 workspace 布局用），非用户级为 null */
    private final Long ownerUserId;
    /** 系统提示，可空 */
    private final String systemPrompt;
    /** 推理参数：最大迭代轮数，可空取运行时默认 */
    private final Integer maxIters;
    /** 调用参数组（temperature/topP/maxTokens），可空取运行时默认 */
    private final GenerateOptions generateOptions;
    /** 执行环境配置（workspace/沙箱/能力），null = 全关（纯对话智能体） */
    private final ExecutionEnvConfig executionEnv;
    /** 模型所属渠道（密钥明文仅存在于运行时装配链路，不进入出参 DTO） */
    private final Channel channel;
    /** 引用的模型元数据 */
    private final Model model;

    private AgentRuntimeConfig(String sessionKey, String userId, String agentName, String specCode,
                               OwnerLevel ownerLevel, Long ownerUserId, String systemPrompt,
                               Integer maxIters, GenerateOptions generateOptions,
                               ExecutionEnvConfig executionEnv, Channel channel, Model model) {
        this.sessionKey = sessionKey;
        this.userId = userId;
        this.agentName = agentName;
        this.specCode = specCode;
        this.ownerLevel = ownerLevel;
        this.ownerUserId = ownerUserId;
        this.systemPrompt = systemPrompt;
        this.maxIters = maxIters;
        this.generateOptions = generateOptions;
        this.executionEnv = executionEnv;
        this.channel = channel;
        this.model = model;
    }

    /**
     * 构建装配指令
     *
     * @param sessionKey 会话标识，不能为空白
     * @param userId     运行时用户标识，不能为空白
     * @param agentName  智能体名，不能为空白
     * @param specCode   业务编码，不能为空白
     * @param ownerLevel 归属层级，不能为 null
     * @param ownerUserId 归属用户编号，非用户级为 null
     * @param systemPrompt 系统提示，可空
     * @param maxIters   最大迭代轮数，可空
     * @param generateOptions 调用参数组，可空
     * @param executionEnv 执行环境配置，可空 = 全关
     * @param channel    渠道聚合，不能为 null
     * @param model      模型聚合，不能为 null
     */
    public static AgentRuntimeConfig of(String sessionKey, String userId, String agentName,
                                        String specCode, OwnerLevel ownerLevel, Long ownerUserId,
                                        String systemPrompt, Integer maxIters,
                                        GenerateOptions generateOptions, ExecutionEnvConfig executionEnv,
                                        Channel channel, Model model) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("会话标识不能为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("运行时用户标识不能为空");
        }
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("智能体名不能为空");
        }
        if (specCode == null || specCode.isBlank()) {
            throw new IllegalArgumentException("业务编码不能为空");
        }
        if (ownerLevel == null) {
            throw new IllegalArgumentException("归属层级不能为空");
        }
        if (channel == null) {
            throw new IllegalArgumentException("运行时装配必须指定渠道");
        }
        if (model == null) {
            throw new IllegalArgumentException("运行时装配必须指定模型");
        }
        return new AgentRuntimeConfig(sessionKey.strip(), userId.strip(), agentName.strip(),
                specCode.strip(), ownerLevel, ownerUserId, systemPrompt, maxIters,
                generateOptions, executionEnv, channel, model);
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getUserId() {
        return userId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getSpecCode() {
        return specCode;
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

    public ExecutionEnvConfig getExecutionEnv() {
        return executionEnv;
    }

    public Channel getChannel() {
        return channel;
    }

    public Model getModel() {
        return model;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentRuntimeConfig other)) {
            return false;
        }
        return Objects.equals(sessionKey, other.sessionKey)
                && Objects.equals(userId, other.userId)
                && Objects.equals(agentName, other.agentName)
                && Objects.equals(specCode, other.specCode)
                && ownerLevel == other.ownerLevel
                && Objects.equals(ownerUserId, other.ownerUserId)
                && Objects.equals(systemPrompt, other.systemPrompt)
                && Objects.equals(maxIters, other.maxIters)
                && Objects.equals(generateOptions, other.generateOptions)
                && Objects.equals(executionEnv, other.executionEnv)
                && Objects.equals(channel, other.channel)
                && Objects.equals(model, other.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionKey, userId, agentName, specCode, ownerLevel, ownerUserId,
                systemPrompt, maxIters, generateOptions, executionEnv, channel, model);
    }

}
