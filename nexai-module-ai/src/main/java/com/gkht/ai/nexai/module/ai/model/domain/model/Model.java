package com.gkht.ai.nexai.module.ai.model.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 模型聚合根（充血模型，零框架依赖）。挂载于某渠道下的具体模型元数据，供 AgentSpec 按元数据引用。
 *
 * <p>agentscope 无模型目录发现能力（调研 3.1 节），标识、上下文窗口、单价、能力标签全自管；
 * 单价口径为「元 / 百万 tokens」，M3 成本归集沿用。</p>
 */
public class Model {

    /** 能力标签单个长度上限 */
    static final int CAPABILITY_MAX_LENGTH = 32;
    /** 能力标签数量上限 */
    static final int CAPABILITIES_MAX_SIZE = 16;

    /** 编号，未落库时为 null */
    private Long id;
    /** 所属渠道编号（外部聚合引用） */
    private Long channelId;
    /** 模型标识（调用时传给提供商的 ID，如 gpt-4o / qwen-plus） */
    private String modelId;
    /** 显示名 */
    private String name;
    /** 上下文窗口（tokens），未知为 null */
    private Integer contextWindow;
    /** 输入单价（元 / 百万 tokens），未定价为 null */
    private BigDecimal inputPrice;
    /** 输出单价（元 / 百万 tokens），未定价为 null */
    private BigDecimal outputPrice;
    /** 能力标签（规范化：去空白/去重/转小写/保序，不可变） */
    private List<String> capabilities;
    /** 是否启用 */
    private boolean enabled;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private Model(Long id, Long channelId, String modelId, String name, Integer contextWindow,
                  BigDecimal inputPrice, BigDecimal outputPrice, List<String> capabilities,
                  boolean enabled, LocalDateTime createTime) {
        this.id = id;
        this.channelId = channelId;
        this.modelId = modelId;
        this.name = name;
        this.contextWindow = contextWindow;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
        this.capabilities = capabilities;
        this.enabled = enabled;
        this.createTime = createTime;
    }

    /**
     * 登记模型，初始为启用状态
     *
     * @param channelId   所属渠道编号，不能为 null
     * @param modelId     模型标识，不能为空白
     * @param name        显示名，不能为空白
     * @param contextWindow 上下文窗口（tokens），可空表示未知
     * @param inputPrice  输入单价（元 / 百万 tokens），可空表示未定价
     * @param outputPrice 输出单价（元 / 百万 tokens），可空表示未定价
     * @param capabilities 能力标签，可空视为无标签
     */
    public static Model create(Long channelId, String modelId, String name, Integer contextWindow,
                               BigDecimal inputPrice, BigDecimal outputPrice, List<String> capabilities) {
        validate(channelId, modelId, name, contextWindow, inputPrice, outputPrice);
        return new Model(null, channelId, modelId.strip(), name.strip(), contextWindow,
                inputPrice, outputPrice, normalizeCapabilities(capabilities), true, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复不做规范化）
     */
    public static Model reconstitute(Long id, Long channelId, String modelId, String name,
                                     Integer contextWindow, BigDecimal inputPrice, BigDecimal outputPrice,
                                     List<String> capabilities, boolean enabled, LocalDateTime createTime) {
        return new Model(id, channelId, modelId, name, contextWindow, inputPrice, outputPrice,
                capabilities == null ? List.of() : List.copyOf(capabilities), enabled, createTime);
    }

    /**
     * 更新模型元数据（全量替换，可改挂渠道）
     */
    public void update(Long channelId, String modelId, String name, Integer contextWindow,
                       BigDecimal inputPrice, BigDecimal outputPrice, List<String> capabilities) {
        validate(channelId, modelId, name, contextWindow, inputPrice, outputPrice);
        this.channelId = channelId;
        this.modelId = modelId.strip();
        this.name = name.strip();
        this.contextWindow = contextWindow;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
        this.capabilities = normalizeCapabilities(capabilities);
    }

    /**
     * 创建与更新共用的校验
     */
    private static void validate(Long channelId, String modelId, String name, Integer contextWindow,
                                 BigDecimal inputPrice, BigDecimal outputPrice) {
        if (channelId == null) {
            throw new IllegalArgumentException("模型所属渠道不能为空");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("模型显示名不能为空");
        }
        if (contextWindow != null && contextWindow < 0) {
            throw new IllegalArgumentException("上下文窗口不能为负数");
        }
        if (inputPrice != null && inputPrice.signum() < 0) {
            throw new IllegalArgumentException("输入单价不能为负数");
        }
        if (outputPrice != null && outputPrice.signum() < 0) {
            throw new IllegalArgumentException("输出单价不能为负数");
        }
    }

    /**
     * 能力标签规范化：去空白、去空项、转小写、去重保序；同时约束单个长度与总数
     */
    private static List<String> normalizeCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String capability : capabilities) {
            if (capability == null) {
                continue;
            }
            String stripped = capability.strip().toLowerCase();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.length() > CAPABILITY_MAX_LENGTH) {
                throw new IllegalArgumentException("能力标签单个不能超过 " + CAPABILITY_MAX_LENGTH + " 个字符");
            }
            normalized.add(stripped);
        }
        if (normalized.size() > CAPABILITIES_MAX_SIZE) {
            throw new IllegalArgumentException("能力标签数量不能超过 " + CAPABILITIES_MAX_SIZE + " 个");
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    /**
     * 启用模型
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * 停用模型：保留数据，仅退出可用范围
     */
    public void disable() {
        this.enabled = false;
    }

    public Long getId() {
        return id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public String getModelId() {
        return modelId;
    }

    public String getName() {
        return name;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public BigDecimal getInputPrice() {
        return inputPrice;
    }

    public BigDecimal getOutputPrice() {
        return outputPrice;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Model other)) {
            return false;
        }
        // 聚合根按身份（编号）判等；未落库的聚合只与自身相等
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
