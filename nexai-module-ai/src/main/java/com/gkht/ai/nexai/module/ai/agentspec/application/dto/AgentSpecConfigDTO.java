package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 智能体规格配置（草稿与版本快照共用结构，按 agentscope 分层组织）")
@Data
public class AgentSpecConfigDTO {

    // —— agent 层 ——

    @Schema(description = "模型引用编号", example = "1")
    private Long modelId;

    @Schema(description = "模型显示名（服务端补充，便于前端展示）", example = "GPT-4o 主力")
    private String modelName;

    @Schema(description = "给 LLM 的自描述（用于展示与子智能体路由）", example = "企业智能客服，处理售前与售后咨询")
    private String description;

    @Schema(description = "系统提示", example = "你是企业的智能客服")
    private String systemPrompt;

    @Schema(description = "推理参数：最大迭代轮数，null 表示运行时取默认", example = "10")
    private Integer maxIters;

    // —— 模型调用层 ——

    @Schema(description = "调用参数组，null 表示全默认")
    private GenerateOptionsDTO generateOptions;

    // —— 挂载层（M2 预留）——

    @Schema(description = "技能引用列表", example = "[1]")
    private List<Long> skillIds;

    @Schema(description = "MCP 服务挂载列表")
    private List<McpServerMountDTO> mcpServers;

    @Schema(description = "子智能体挂载列表")
    private List<SubagentMountDTO> subagents;

    // —— 执行环境层 ——

    @Schema(description = "执行环境配置，null 表示全关（纯对话智能体）")
    private ExecutionEnvDTO executionEnv;

}
