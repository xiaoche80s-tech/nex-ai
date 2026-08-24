package com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject;

/**
 * MCP Server 归属维度（与 Channel 的双归属字段同构）：MVP 固定租户侧（BYO-MCP），平台级预留。
 */
public enum McpOwnerType {

    /** 平台共享（预留，MVP 不开放） */
    PLATFORM,

    /** 租户自注册 */
    TENANT

}
