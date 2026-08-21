package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

    @Schema(description = "描述", example = "回答客户咨询的智能客服")
    @Size(max = 512, message = "规格描述不能超过 512 个字符")
    private String description;

    @Schema(description = "图标标识", example = "ep:service")
    @Size(max = 128, message = "图标标识不能超过 128 个字符")
    private String icon;

    @Schema(description = "模型引用编号（ai_model.id）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "规格必须引用一个模型")
    private Long modelId;

    @Schema(description = "系统提示", example = "你是企业的智能客服")
    @Size(max = 16384, message = "系统提示不能超过 16384 个字符")
    private String systemPrompt;

    @Schema(description = "推理参数：最大迭代轮数，不填运行时取默认", example = "10")
    @Min(value = 1, message = "最大迭代轮数不能小于 1")
    private Integer maxIters;

    @Schema(description = "推理参数：温度（0 ~ 2），不填运行时取默认", example = "0.7")
    @DecimalMin(value = "0", message = "温度不能小于 0")
    @DecimalMax(value = "2", message = "温度不能大于 2")
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
