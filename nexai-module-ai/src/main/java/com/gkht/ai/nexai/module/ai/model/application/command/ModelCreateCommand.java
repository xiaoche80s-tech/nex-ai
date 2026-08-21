package com.gkht.ai.nexai.module.ai.model.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 模型创建命令")
@Data
public class ModelCreateCommand {

    @Schema(description = "所属渠道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属渠道不能为空")
    private Long channelId;

    @Schema(description = "模型标识（调用时传给提供商的 ID）", requiredMode = Schema.RequiredMode.REQUIRED, example = "gpt-4o")
    @NotBlank(message = "模型标识不能为空")
    @Size(max = 128, message = "模型标识不能超过 128 个字符")
    private String modelId;

    @Schema(description = "显示名", requiredMode = Schema.RequiredMode.REQUIRED, example = "GPT-4o 主力")
    @NotBlank(message = "模型显示名不能为空")
    @Size(max = 64, message = "模型显示名不能超过 64 个字符")
    private String name;

    @Schema(description = "上下文窗口（tokens），未知不填", example = "128000")
    @Min(value = 0, message = "上下文窗口不能为负数")
    private Integer contextWindow;

    @Schema(description = "输入单价（元 / 百万 tokens），未定价不填", example = "0.5")
    @DecimalMin(value = "0", message = "输入单价不能为负数")
    private BigDecimal inputPrice;

    @Schema(description = "输出单价（元 / 百万 tokens），未定价不填", example = "2.5")
    @DecimalMin(value = "0", message = "输出单价不能为负数")
    private BigDecimal outputPrice;

    @Schema(description = "能力标签列表", example = "[\"chat\", \"vision\"]")
    @Size(max = 16, message = "能力标签数量不能超过 16 个")
    private List<String> capabilities;

}
