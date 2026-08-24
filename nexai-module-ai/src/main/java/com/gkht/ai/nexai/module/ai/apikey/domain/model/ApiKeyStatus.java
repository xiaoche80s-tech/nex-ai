package com.gkht.ai.nexai.module.ai.apikey.domain.model;

/**
 * 租户 API Key 状态（工单 16）。
 */
public enum ApiKeyStatus {

    /** 可用 */
    ENABLED,

    /** 已吊销（泄漏止损；校验直接拒绝） */
    REVOKED

}
