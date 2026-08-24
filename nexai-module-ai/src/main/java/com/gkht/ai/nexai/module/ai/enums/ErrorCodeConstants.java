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
    ErrorCode AGENT_SPEC_FOLDER_FILE_INVALID = new ErrorCode(1_022_004_014, "规格私有文件夹文件上传失败：{}");

    // ========== 会话 SESSION 1-022-005-000 ==========
    ErrorCode SESSION_NOT_EXISTS = new ErrorCode(1_022_005_000, "会话不存在");
    ErrorCode SESSION_ASSEMBLE_INVALID = new ErrorCode(1_022_005_001, "会话装配失败：{}");
    ErrorCode SESSION_ASKING_CONFIRM_REQUIRED = new ErrorCode(1_022_005_002, "会话挂起中，请先完成工具审批");

    // ========== Skill 资产 SKILL 1-022-006-000 ==========
    ErrorCode SKILL_NOT_EXISTS = new ErrorCode(1_022_006_000, "Skill 不存在");
    ErrorCode SKILL_NAME_DUPLICATE = new ErrorCode(1_022_006_001, "同归属下技能名称已存在：{}");
    ErrorCode SKILL_OWNER_LEVEL_UNSUPPORTED = new ErrorCode(1_022_006_002, "MVP 仅支持租户级（TENANT）与用户级（USER）Skill，平台级后置开放");
    ErrorCode SKILL_CONFIG_INVALID = new ErrorCode(1_022_006_003, "Skill 配置校验失败：{}");
    ErrorCode SKILL_TENANT_CONTEXT_MISSING = new ErrorCode(1_022_006_004, "租户上下文缺失，无法物化 Skill");

    // ========== MCP Server 1-022-007-000 ==========
    ErrorCode MCP_SERVER_NOT_EXISTS = new ErrorCode(1_022_007_000, "MCP Server 不存在");
    ErrorCode MCP_SERVER_CONFIG_INVALID = new ErrorCode(1_022_007_002, "MCP Server 配置校验失败：{}");
    ErrorCode MCP_SERVER_TOOLS_WHITELIST_INVALID = new ErrorCode(1_022_007_004, "工具白名单中的工具不在该 Server 最近拉取的工具清单内：{}");
    ErrorCode MCP_MOUNT_UNAVAILABLE = new ErrorCode(1_022_007_005, "MCP 工具挂载不可用：{}（装配已降级跳过，该来源工具暂不可调用）");

    // ========== 租户 API Key 1-022-010-000（工单 16） ==========
    ErrorCode API_KEY_NOT_EXISTS = new ErrorCode(1_022_010_000, "API Key 不存在");
    ErrorCode API_KEY_INVALID = new ErrorCode(1_022_010_001, "API Key 配置校验失败：{}");
    ErrorCode API_KEY_UNAUTHORIZED = new ErrorCode(1_022_010_002, "API Key 无效或已吊销");
    ErrorCode API_KEY_SPEC_FORBIDDEN = new ErrorCode(1_022_010_003, "API Key 不在目标智能体的放行规格范围内");

}
