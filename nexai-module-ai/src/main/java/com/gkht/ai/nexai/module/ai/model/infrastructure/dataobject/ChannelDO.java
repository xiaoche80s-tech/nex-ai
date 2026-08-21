package com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.mybatis.core.type.EncryptTypeHandler;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道 DO（贫血模型）。api_key 列经 EncryptTypeHandler 以 AES 密文落库与解密读取。
 */
@TableName(value = "ai_channel", autoResultMap = true)
@KeySequence("ai_channel_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelDO extends TenantBaseDO {

    /**
     * 渠道编号
     */
    @TableId
    private Long id;
    /**
     * 渠道名称，同一提供商多渠道时用于区分
     */
    private String name;
    /**
     * 提供商类型编码，见 ChannelProvider
     */
    private String provider;
    /**
     * 端点地址
     */
    private String baseUrl;
    /**
     * API 密钥（AES 密文落库）
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String apiKey;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 归属维度编码（platform / tenant），见 ChannelOwnerType
     */
    private String ownerType;

}
