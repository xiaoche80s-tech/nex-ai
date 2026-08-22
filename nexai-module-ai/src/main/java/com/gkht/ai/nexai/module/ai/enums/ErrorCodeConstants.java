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

    // ========== 模型元数据 MODEL 1-022-003-000 ==========
    ErrorCode MODEL_NOT_EXISTS = new ErrorCode(1_022_003_000, "模型不存在");
    ErrorCode MODEL_DUPLICATE_MODEL_ID = new ErrorCode(1_022_003_001, "同渠道下模型标识已存在");

    // ========== 智能体规格 AGENT_SPEC 1-022-004-000 ==========
    ErrorCode AGENT_SPEC_NOT_EXISTS = new ErrorCode(1_022_004_000, "智能体规格不存在");
    ErrorCode AGENT_SPEC_PUBLISH_WITHOUT_DRAFT = new ErrorCode(1_022_004_001, "规格当前没有可发布的草稿，请先编辑生成新草稿");
    ErrorCode AGENT_SPEC_VERSION_NOT_EXISTS = new ErrorCode(1_022_004_002, "规格版本不存在");
    ErrorCode AGENT_SPEC_SELF_MOUNTING_REJECTED = new ErrorCode(1_022_004_003, "规格不能把自己挂载为子智能体");

    // ========== 会话 SESSION 1-022-005-000 ==========
    ErrorCode SESSION_NOT_EXISTS = new ErrorCode(1_022_005_000, "会话不存在");
    ErrorCode SESSION_SPEC_NOT_PUBLISHED = new ErrorCode(1_022_005_001, "规格尚未发布任何版本，无法发起会话");
    ErrorCode SESSION_MODEL_UNAVAILABLE = new ErrorCode(1_022_005_002, "会话绑定的模型或渠道已停用或不存在，无法装配运行时");
    ErrorCode SESSION_RUNNING = new ErrorCode(1_022_005_003, "该会话已有运行中的事件流，请先停止或等待其结束");

    // ========== 技能 SKILL 1-022-006-000 ==========
    ErrorCode SKILL_NOT_EXISTS = new ErrorCode(1_022_006_000, "技能不存在");
    ErrorCode SKILL_MD_INVALID = new ErrorCode(1_022_006_001, "SKILL.md 校验失败：{0}");
    ErrorCode SKILL_NAME_DUPLICATE = new ErrorCode(1_022_006_002, "同名技能已存在：{0}");
    ErrorCode SKILL_PUBLISH_WITHOUT_DRAFT = new ErrorCode(1_022_006_003, "技能当前没有可发布的草稿，请先编辑生成新草稿");
    ErrorCode SKILL_VERSION_NOT_EXISTS = new ErrorCode(1_022_006_004, "技能版本不存在");

}
