package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 智能体规格创建命令（携带首个草稿）")
@Data
public class AgentSpecCreateCommand implements AgentSpecDraftCommand {

    @Schema(description = "规格名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "客服助手")
    @NotBlank(message = "规格名称不能为空")
    @Size(max = 64, message = "规格名称不能超过 64 个字符")
    private String name;

    @Schema(description = "图标标识", example = "ep:service")
    @Size(max = 128, message = "图标标识不能超过 128 个字符")
    private String icon;

    @Schema(description = "模型引用编号（ai_model.id）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "规格必须引用一个模型")
    private Long modelId;

    @Schema(description = "给 LLM 的自描述（用于展示与子智能体路由）", requiredMode = Schema.RequiredMode.REQUIRED, example = "企业智能客服，处理售前与售后咨询")
    @NotBlank(message = "规格自描述不能为空")
    @Size(max = 1024, message = "规格自描述不能超过 1024 个字符")
    private String description;

    @Schema(description = "系统提示", example = "你是企业的智能客服")
    @Size(max = 16384, message = "系统提示不能超过 16384 个字符")
    private String systemPrompt;

    @Schema(description = "推理参数：最大迭代轮数，不填运行时取默认", example = "10")
    @Min(value = 1, message = "最大迭代轮数不能小于 1")
    private Integer maxIters;

    @Schema(description = "调用参数：温度（0 ~ 2），不填运行时取默认", example = "0.7")
    @DecimalMin(value = "0", message = "温度不能小于 0")
    @DecimalMax(value = "2", message = "温度不能大于 2")
    private Double temperature;

    @Schema(description = "调用参数：核采样阈值（0 ~ 1），不填运行时取默认", example = "0.9")
    @DecimalMin(value = "0", message = "topP 不能小于 0")
    @DecimalMax(value = "1", message = "topP 不能大于 1")
    private Double topP;

    @Schema(description = "调用参数：单次生成的最大 tokens，不填运行时取默认", example = "4096")
    @Min(value = 1, message = "最大 tokens 不能小于 1")
    private Integer maxTokens;

    @Schema(description = "技能引用列表（M2 预留）", example = "[1]")
    private List<Long> skillIds;

    @Schema(description = "知识库引用列表（M2 预留）", example = "[1]")
    private List<Long> knowledgeBaseIds;

    @Schema(description = "MCP 服务挂载列表（M2 预留）")
    private List<McpServerMountCommand> mcpServers;

    @Schema(description = "子智能体挂载列表（M2 预留）")
    private List<SubagentMountCommand> subagents;

}
