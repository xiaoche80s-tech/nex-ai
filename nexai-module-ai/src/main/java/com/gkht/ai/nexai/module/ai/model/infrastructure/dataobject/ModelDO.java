package com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 模型 DO（贫血模型）。capabilities 列存 JSON 数组字符串（如 ["chat","vision"]），与领域 List 的互转在 Converter。
 */
@TableName(value = "ai_model", autoResultMap = true)
@KeySequence("ai_model_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelDO extends TenantBaseDO {

    /**
     * 模型编号
     */
    @TableId
    private Long id;
    /**
     * 所属渠道编号
     */
    private Long channelId;
    /**
     * 模型标识（调用时传给提供商的 ID）
     */
    private String modelId;
    /**
     * 显示名
     */
    private String name;
    /**
     * 上下文窗口（tokens），null 表示未知
     */
    private Integer contextWindow;
    /**
     * 输入单价（元 / 百万 tokens），null 表示未定价
     */
    private BigDecimal inputPrice;
    /**
     * 输出单价（元 / 百万 tokens），null 表示未定价
     */
    private BigDecimal outputPrice;
    /**
     * 能力标签 JSON 数组字符串
     */
    private String capabilities;
    /**
     * 是否启用
     */
    private Boolean enabled;

}
