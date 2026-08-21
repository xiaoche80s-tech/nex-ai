package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 智能体规格配置（草稿与版本快照共用结构）")
@Data
public class AgentSpecConfigDTO {

    @Schema(description = "模型引用编号", example = "1")
    private Long modelId;

    @Schema(description = "模型显示名（服务端补充，便于前端展示）", example = "GPT-4o 主力")
    private String modelName;

    @Schema(description = "系统提示", example = "你是企业的智能客服")
    private String systemPrompt;

    @Schema(description = "推理参数：最大迭代轮数，null 表示运行时取默认", example = "10")
    private Integer maxIters;

    @Schema(description = "推理参数：温度，null 表示运行时取默认", example = "0.7")
    private Double temperature;

    @Schema(description = "技能引用列表（M2 预留）", example = "[1]")
    private List<Long> skillIds;

    @Schema(description = "知识库引用列表（M2 预留）", example = "[1]")
    private List<Long> knowledgeBaseIds;

    @Schema(description = "MCP 服务引用列表（M2 预留）", example = "[1]")
    private List<Long> mcpServerIds;

    @Schema(description = "子智能体规格引用列表（M2 预留）", example = "[2]")
    private List<Long> subagentSpecIds;

}
