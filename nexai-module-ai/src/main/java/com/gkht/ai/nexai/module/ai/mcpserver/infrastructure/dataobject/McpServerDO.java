package com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.mybatis.core.type.EncryptTypeHandler;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP Server DO（贫血模型）。列表/Map 字段以 JSON 字符串落库（converter 桥接）；
 * headers 序列化后经 EncryptTypeHandler 以 AES 密文落库（可能携带 Bearer token）。
 */
@TableName(value = "ai_mcp_server", autoResultMap = true)
@KeySequence("ai_mcp_server_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class McpServerDO extends TenantBaseDO {

    /**
     * MCP Server 编号
     */
    @TableId
    private Long id;
    /**
     * 名称
     */
    private String name;
    /**
     * 传输类型编码，见 McpTransport
     */
    private String transport;
    /**
     * 端点地址（SSE / Streamable HTTP）
     */
    private String endpoint;
    /**
     * 启动命令（stdio）
     */
    private String command;
    /**
     * stdio 启动参数（JSON 数组）
     */
    private String args;
    /**
     * stdio 环境变量（JSON 对象）
     */
    private String env;
    /**
     * 认证头（JSON 对象序列化后 AES 密文落库）
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String headers;
    /**
     * 请求超时（秒），null = 运行时默认
     */
    private Integer timeoutSeconds;
    /**
     * 工具白名单（JSON 数组），空 = 全部工具
     */
    private String allowedTools;
    /**
     * 最近探测拉取的工具名清单（JSON 数组，信息性缓存）
     */
    private String availableTools;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 归属维度编码，见 McpOwnerType
     */
    private String ownerType;

}
