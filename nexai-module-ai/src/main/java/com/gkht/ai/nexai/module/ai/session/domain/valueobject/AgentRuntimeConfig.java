package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.model.Model;

import java.util.Objects;

/**
 * 运行时装配指令值对象（不可变）：把「渠道 + 模型 + 规格版本快照 + 会话寻址」
 * 一次性交给运行时端口。字段直接复用 model 聚合的领域类型——这里是一次性调用的
 * 瞬时传值（跨聚合只读），不是聚合间的持久化引用（持久化仍只引用聚合根 ID），
 * 使端口契约不含 agentscope 类型（ADR-0001），也不退化为一堆松散 String。
 */
public final class AgentRuntimeConfig {

    /** 会话标识（agentscope 状态存储的 sessionId） */
    private final String sessionKey;
    /** 运行时用户标识（agentscope 状态存储的 userId 槽位，取登录用户编号） */
    private final String userId;
    /** 智能体名（agentscope agent name，用于追踪与日志） */
    private final String agentName;
    /** 系统提示，可空 */
    private final String systemPrompt;
    /** 推理参数：最大迭代轮数，可空取运行时默认 */
    private final Integer maxIters;
    /** 推理参数：温度，可空取运行时默认 */
    private final Double temperature;
    /** 模型所属渠道（密钥明文仅存在于运行时装配链路，不进入出参 DTO） */
    private final Channel channel;
    /** 引用的模型元数据 */
    private final Model model;

    private AgentRuntimeConfig(String sessionKey, String userId, String agentName, String systemPrompt,
                               Integer maxIters, Double temperature, Channel channel, Model model) {
        this.sessionKey = sessionKey;
        this.userId = userId;
        this.agentName = agentName;
        this.systemPrompt = systemPrompt;
        this.maxIters = maxIters;
        this.temperature = temperature;
        this.channel = channel;
        this.model = model;
    }

    /**
     * 构建装配指令
     *
     * @param sessionKey 会话标识，不能为空白
     * @param userId     运行时用户标识，不能为空白
     * @param agentName  智能体名，不能为空白
     * @param systemPrompt 系统提示，可空
     * @param maxIters   最大迭代轮数，可空
     * @param temperature 温度，可空
     * @param channel    渠道聚合，不能为 null
     * @param model      模型聚合，不能为 null
     */
    public static AgentRuntimeConfig of(String sessionKey, String userId, String agentName,
                                        String systemPrompt, Integer maxIters, Double temperature,
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
        if (channel == null) {
            throw new IllegalArgumentException("运行时装配必须指定渠道");
        }
        if (model == null) {
            throw new IllegalArgumentException("运行时装配必须指定模型");
        }
        return new AgentRuntimeConfig(sessionKey.strip(), userId.strip(), agentName.strip(),
                systemPrompt, maxIters, temperature, channel, model);
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

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Integer getMaxIters() {
        return maxIters;
    }

    public Double getTemperature() {
        return temperature;
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
                && Objects.equals(systemPrompt, other.systemPrompt)
                && Objects.equals(maxIters, other.maxIters)
                && Objects.equals(temperature, other.temperature)
                && Objects.equals(channel, other.channel)
                && Objects.equals(model, other.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionKey, userId, agentName, systemPrompt,
                maxIters, temperature, channel, model);
    }

}
