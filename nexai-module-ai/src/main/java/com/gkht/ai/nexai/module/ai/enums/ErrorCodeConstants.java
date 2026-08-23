package com.gkht.ai.nexai.module.ai.enums;

import com.gkht.ai.nexai.framework.common.exception.ErrorCode;

/**
 * AI 模块错误码枚举类
 *
 * ai 智能体平台，使用 1-022-000-000 段（对齐芋道官方 ai 模块号段约定）。
 * 各聚合子段分配：channel 1-022-002（模型元数据沿用 003）、agentspec 1-022-004、
 * session 1-022-005、skill 1-022-006、mcpserver 1-022-007、audit 1-022-008、usage 1-022-009。
 * 错误码随各聚合工单按需注册，此处先立注册表与段约定。
 */
public interface ErrorCodeConstants {

    // ========== 模型渠道 CHANNEL 1-022-002-000 ==========
    ErrorCode CHANNEL_NOT_EXISTS = new ErrorCode(1_022_002_000, "渠道不存在");
    ErrorCode CHANNEL_PROVIDER_INVALID = new ErrorCode(1_022_002_001, "渠道提供商类型不支持");
    ErrorCode CHANNEL_INVALID = new ErrorCode(1_022_002_002, "渠道配置校验失败：{}");

    // ========== 模型元数据 MODEL 1-022-003-000 ==========
    ErrorCode MODEL_NOT_EXISTS = new ErrorCode(1_022_003_000, "模型不存在");
    ErrorCode MODEL_DUPLICATE_MODEL_ID = new ErrorCode(1_022_003_001, "同渠道下模型标识已存在：{}");
    ErrorCode MODEL_INVALID = new ErrorCode(1_022_003_002, "模型配置校验失败：{}");

    // ========== 智能体规格 AGENT_SPEC 1-022-004-000 ==========
    ErrorCode AGENT_SPEC_CODE_DUPLICATE = new ErrorCode(1_022_004_004, "同归属层级下业务编码已存在：{}");
    ErrorCode AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED = new ErrorCode(1_022_004_006, "MVP 仅支持租户级（TENANT）与用户级（USER）归属，平台级后置开放");
    ErrorCode AGENT_SPEC_USER_OWNER_LOGIN_REQUIRED = new ErrorCode(1_022_004_007, "用户级规格必须登录后创建（归属用户取当前登录态）");
    ErrorCode AGENT_SPEC_CONFIG_INVALID = new ErrorCode(1_022_004_008, "规格配置校验失败：{}");
    ErrorCode AGENT_SPEC_NOT_EXISTS = new ErrorCode(1_022_004_010, "规格不存在");
    ErrorCode AGENT_SPEC_PUBLISH_INVALID = new ErrorCode(1_022_004_011, "规格发布失败：{}");
    ErrorCode AGENT_SPEC_VERSION_NOT_EXISTS = new ErrorCode(1_022_004_012, "目标版本不存在");
    ErrorCode AGENT_SPEC_VERSION_CONFLICT = new ErrorCode(1_022_004_013, "版本发布冲突，请重试");

}
