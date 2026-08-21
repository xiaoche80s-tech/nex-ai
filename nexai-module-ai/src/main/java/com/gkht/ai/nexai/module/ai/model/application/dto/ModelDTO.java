package com.gkht.ai.nexai.module.ai.model.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 模型 DTO")
@Data
public class ModelDTO {

    @Schema(description = "模型编号", example = "1")
    private Long id;

    @Schema(description = "所属渠道编号", example = "1")
    private Long channelId;

    @Schema(description = "所属渠道名称（服务端补充，便于列表展示）", example = "公司采购的 OpenAI 主渠道")
    private String channelName;

    @Schema(description = "所属渠道提供商类型编码", example = "openai")
    private String channelProvider;

    @Schema(description = "模型标识", example = "gpt-4o")
    private String modelId;

    @Schema(description = "显示名", example = "GPT-4o 主力")
    private String name;

    @Schema(description = "上下文窗口（tokens），未知为 null", example = "128000")
    private Integer contextWindow;

    @Schema(description = "输入单价（元 / 百万 tokens），未定价为 null", example = "0.5")
    private BigDecimal inputPrice;

    @Schema(description = "输出单价（元 / 百万 tokens），未定价为 null", example = "2.5")
    private BigDecimal outputPrice;

    @Schema(description = "能力标签列表", example = "[\"chat\", \"vision\"]")
    private List<String> capabilities;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
