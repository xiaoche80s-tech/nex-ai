package com.gkht.ai.nexai.module.ai.shared.runtime;

/**
 * RuntimeContext extra 维度键常量（工单 14，工单 21 上移 shared——事实上的
 * shared kernel：session 装配网关注入、audit/usage 采集 middleware 读取，
 * 三聚合共用同一口径，防字符串键漂移）。tenantId/sessionKey/agentId/specId/versionNo 五维。
 */
public interface RuntimeContextKeys {

    /** 租户编号（Long） */
    String TENANT_ID = "tenantId";
    /** 会话业务键（String，= agentscope 槽位 sessionId） */
    String SESSION_KEY = "sessionKey";
    /** agentId（String，= specCode） */
    String AGENT_ID = "agentId";
    /** 规格编号（Long，采集规格维度） */
    String SPEC_ID = "specId";
    /** 规格版本号（Integer，采集规格维度） */
    String VERSION_NO = "versionNo";

}
