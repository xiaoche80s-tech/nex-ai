package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.ToolMountCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import lombok.Data;

import java.util.List;

/**
 * 智能体规格创建命令（携带首个草稿）。调用参数平铺（与领域四层结构解耦），
 * 由应用服务组装为分层配置值对象；跨字段的联动校验（执行环境链）在领域层完成。
 */
@Schema(description = "管理后台 - 智能体规格创建命令（携带首个草稿）")
@Data
public class AgentSpecCreateCommand {

    // —— 主体元数据（spec_code 与归属创建后不可变） ——

    @Schema(description = "规格名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "客服助手")
    @NotBlank(message = "规格名称不能为空")
    @Size(max = AgentSpec.NAME_MAX_LENGTH,
            message = "规格名称不能超过 " + AgentSpec.NAME_MAX_LENGTH + " 个字符")
    private String name;

    @Schema(description = "业务编码（slug，创建后不可变）", requiredMode = Schema.RequiredMode.REQUIRED, example = "customer-service")
    @NotBlank(message = "业务编码不能为空")
    @Pattern(regexp = AgentSpec.SPEC_CODE_REGEX,
            message = "业务编码须为小写字母开头的小写字母/数字/连字符组合（2~64 位）")
    private String specCode;

    @Schema(description = "归属层级（MVP 开放 TENANT/USER，平台级后置；不填默认租户级）", example = "TENANT")
    @Pattern(regexp = "TENANT|USER", message = "MVP 仅支持租户级（TENANT）与用户级（USER）归属")
    private String ownerLevel;

    @Schema(description = "图标标识", example = "ep:service")
    @Size(max = AgentSpec.ICON_MAX_LENGTH,
            message = "图标标识不能超过 " + AgentSpec.ICON_MAX_LENGTH + " 个字符")
    private String icon;

    // —— agent 层（模型管理落地前 modelId 可空，发布时校验补齐） ——

    @Schema(description = "模型引用编号（ai_model.id），草稿态可空", example = "1")
    private Long modelId;

    @Schema(description = "自描述（列表展示用）", example = "企业智能客服，处理售前与售后咨询")
    @Size(max = AgentSpecConfig.DESCRIPTION_MAX_LENGTH,
            message = "规格自描述不能超过 " + AgentSpecConfig.DESCRIPTION_MAX_LENGTH + " 个字符")
    private String description;

    @Schema(description = "系统提示", example = "你是企业的智能客服")
    @Size(max = AgentSpecConfig.SYSTEM_PROMPT_MAX_LENGTH,
            message = "系统提示不能超过 " + AgentSpecConfig.SYSTEM_PROMPT_MAX_LENGTH + " 个字符")
    private String systemPrompt;

    @Schema(description = "推理参数：最大迭代轮数，不填运行时取默认", example = "10")
    @Min(value = 1, message = "最大迭代轮数不能小于 1")
    private Integer maxIters;

    // —— 模型调用层 ——

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

    // —— 挂载层（MVP 仅建模，编辑面后置） ——

    @Schema(description = "技能引用列表（编辑面后置）", example = "[1]")
    private List<Long> skillIds;

    @Schema(description = "工具挂载列表（来源 + 引用 + 可选白名单，编辑面后置）")
    private List<ToolMountCommand> tools;

    @Schema(description = "规格私有文件夹挂载列表（ASSET 资料文件夹 / TOOLSET 工具集文件夹，工单 18）")
    private List<FolderMountCommand> folders;

    // —— 执行环境层 ——

    @Schema(description = "是否启用 workspace（文件工具与落盘总开关）", example = "false")
    private Boolean workspaceEnabled;

    @Schema(description = "是否启用 Docker 沙箱（仅 workspace 启用时可选）", example = "false")
    private Boolean sandboxEnabled;

    @Schema(description = "沙箱内开放的执行能力（SHELL/PYTHON/NODE，仅沙箱模式可选）", example = "[\"PYTHON\"]")
    private List<@Pattern(regexp = ExecutionCapability.REGEX,
                    message = "执行能力仅支持 SHELL/PYTHON/NODE") String> capabilities;

}
