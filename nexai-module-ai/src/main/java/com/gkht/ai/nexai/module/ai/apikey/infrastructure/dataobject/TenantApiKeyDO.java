package com.gkht.ai.nexai.module.ai.apikey.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户 API Key DO：密文存储（key_hash = SHA-256 hex，明文不落库）；
 * key_prefix 为识别展示面；spec_codes JSON 数组（空 = 本租户全部规格）。
 */
@TableName("ai_tenant_api_key")
@KeySequence("ai_tenant_api_key_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantApiKeyDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** Key 名称 */
    private String name;
    /** 明文 Key 识别前缀（展示面，如 nexai-abc123） */
    private String keyPrefix;
    /** 明文 Key 的 SHA-256 hex（校验面） */
    private String keyHash;
    /** 状态（ENABLED/REVOKED） */
    private String status;
    /** 规格范围（specCode 白名单 JSON 数组，空数组 = 本租户全部规格） */
    @TableField("spec_codes")
    private String specCodes;

}
