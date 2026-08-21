package com.gkht.ai.nexai.module.ai.enums;

import com.gkht.ai.nexai.framework.common.exception.ErrorCode;

/**
 * AI 模块错误码枚举类
 *
 * ai 智能体平台，使用 1-022-000-000 段（对齐芋道官方 ai 模块号段约定）
 */
public interface ErrorCodeConstants {

    // ========== 问题反馈 FEEDBACK 1-022-001-000 ==========
    ErrorCode FEEDBACK_NOT_EXISTS = new ErrorCode(1_022_001_000, "问题反馈不存在");
    ErrorCode FEEDBACK_STATUS_TRANSITION_ILLEGAL = new ErrorCode(1_022_001_001, "问题反馈状态流转不合法");

    // ========== 模型渠道 CHANNEL 1-022-002-000 ==========
    ErrorCode CHANNEL_NOT_EXISTS = new ErrorCode(1_022_002_000, "渠道不存在");
    ErrorCode CHANNEL_PROVIDER_INVALID = new ErrorCode(1_022_002_001, "渠道提供商类型不支持");

}
