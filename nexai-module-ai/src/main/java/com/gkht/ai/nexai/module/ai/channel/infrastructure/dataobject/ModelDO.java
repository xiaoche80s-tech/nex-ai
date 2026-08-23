package com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 模型 DO（贫血模型）。channel_id + model_id 唯一性由应用层预校验 + 生产库唯一索引兜底。
 */
@TableName("ai_model")
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
     * 所属渠道编号（ai_channel.id）
     */
    private Long channelId;
    /**
     * 模型标识（传给提供商的 ID）
     */
    private String modelId;
    /**
     * 显示名
     */
    private String name;
    /**
     * 上下文窗口（tokens），未知为 null
     */
    private Integer contextWindow;
    /**
     * 输入单价（元 / 百万 tokens），未定价为 null
     */
    private BigDecimal inputPrice;
    /**
     * 输出单价（元 / 百万 tokens），未定价为 null
     */
    private BigDecimal outputPrice;
    /**
     * 是否启用
     */
    private Boolean enabled;

}
