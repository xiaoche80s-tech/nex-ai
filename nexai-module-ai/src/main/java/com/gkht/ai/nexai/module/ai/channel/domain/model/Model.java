package com.gkht.ai.nexai.module.ai.channel.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型实体（Channel 聚合内，充血模型，零框架依赖）：挂接在某渠道下的模型元数据，
 * 供 AgentSpec 按编号引用。
 *
 * <p>agentscope 无模型目录发现能力，标识、上下文窗口与计价信息全自管；
 * 单价口径为「元 / 百万 tokens」，后续成本归集（用量工单）沿用。</p>
 */
public class Model {

    /** 模型标识长度上限（字符） */
    static final int MODEL_ID_MAX_LENGTH = 128;
    /** 显示名长度上限（字符） */
    static final int NAME_MAX_LENGTH = 64;

    /** 编号，未落库时为 null */
    private Long id;
    /** 所属渠道编号（聚合根引用） */
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
    /** 是否启用 */
    private boolean enabled;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;
    /** 更新时间，由持久化填充，新建时为 null；参与常驻实例版本戳（工单 08：模型配置变更即失效重建） */
    private LocalDateTime updateTime;

    private Model(Long id, Long channelId, String modelId, String name, Integer contextWindow,
                  BigDecimal inputPrice, BigDecimal outputPrice, boolean enabled,
                  LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.channelId = channelId;
        this.modelId = modelId;
        this.name = name;
        this.contextWindow = contextWindow;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
        this.enabled = enabled;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 登记模型，初始为启用状态
     *
     * @param channelId      所属渠道编号，不能为 null
     * @param modelId        模型标识，不能为空白
     * @param name           显示名，不能为空白
     * @param contextWindow  上下文窗口（tokens），可空表示未知，须为正
     * @param inputPrice     输入单价（元 / 百万 tokens），可空表示未定价，须非负
     * @param outputPrice    输出单价（元 / 百万 tokens），可空表示未定价，须非负
     */
    public static Model create(Long channelId, String modelId, String name, Integer contextWindow,
                               BigDecimal inputPrice, BigDecimal outputPrice) {
        validate(channelId, modelId, name, contextWindow, inputPrice, outputPrice);
        return new Model(null, channelId, modelId.strip(), name.strip(), contextWindow,
                inputPrice, outputPrice, true, null, null);
    }

    /**
     * 从持久化数据重建实体（Repository 专用，字段原样恢复不做规范化）
     */
    public static Model reconstitute(Long id, Long channelId, String modelId, String name,
                                     Integer contextWindow, BigDecimal inputPrice,
                                     BigDecimal outputPrice, boolean enabled,
                                     LocalDateTime createTime, LocalDateTime updateTime) {
        return new Model(id, channelId, modelId, name, contextWindow, inputPrice, outputPrice,
                enabled, createTime, updateTime);
    }

    /**
     * 更新模型元数据（全量替换，可改挂渠道）
     */
    public void update(Long channelId, String modelId, String name, Integer contextWindow,
                       BigDecimal inputPrice, BigDecimal outputPrice) {
        validate(channelId, modelId, name, contextWindow, inputPrice, outputPrice);
        this.channelId = channelId;
        this.modelId = modelId.strip();
        this.name = name.strip();
        this.contextWindow = contextWindow;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
    }

    /**
     * 登记与更新共用的校验
     */
    private static void validate(Long channelId, String modelId, String name, Integer contextWindow,
                                 BigDecimal inputPrice, BigDecimal outputPrice) {
        if (channelId == null) {
            throw new IllegalArgumentException("模型必须挂接渠道");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        if (modelId.strip().length() > MODEL_ID_MAX_LENGTH) {
            throw new IllegalArgumentException("模型标识不能超过 " + MODEL_ID_MAX_LENGTH + " 个字符");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("模型显示名不能为空");
        }
        if (name.strip().length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("模型显示名不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        if (contextWindow != null && contextWindow < 1) {
            throw new IllegalArgumentException("上下文窗口必须为正数");
        }
        if (inputPrice != null && inputPrice.signum() < 0) {
            throw new IllegalArgumentException("输入单价不能为负数");
        }
        if (outputPrice != null && outputPrice.signum() < 0) {
            throw new IllegalArgumentException("输出单价不能为负数");
        }
    }

    /**
     * 启用模型
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * 停用模型：保留数据，仅退出来用范围（引用它的规格不受影响，装配时校验）
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

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /** 更新时间（参与常驻实例版本戳），新建时为 null */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

}
