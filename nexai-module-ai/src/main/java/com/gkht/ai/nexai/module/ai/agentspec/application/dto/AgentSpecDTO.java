package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 智能体规格 DTO（列表项）")
@Data
public class AgentSpecDTO {

    @Schema(description = "规格编号", example = "1")
    private Long id;

    @Schema(description = "规格名称", example = "客服助手")
    private String name;

    @Schema(description = "业务编码（slug，创建后不可变；workspace 目录与运行时标识用）", example = "customer-service")
    private String specCode;

    @Schema(description = "归属层级（PLATFORM/TENANT/USER）", example = "TENANT")
    private String ownerLevel;

    @Schema(description = "归属用户编号（用户级规格 = 创建者），非用户级为 null", example = "1")
    private Long ownerUserId;

    @Schema(description = "描述", example = "回答客户咨询的智能客服")
    private String description;

    @Schema(description = "图标标识", example = "ep:service")
    private String icon;

    @Schema(description = "已发布的最新版本号，从未发布为 0", example = "2")
    private Integer latestVersionNo;

    @Schema(description = "当前默认版本号，从未发布为 null", example = "1")
    private Integer currentVersionNo;

    @Schema(description = "是否有未发布草稿")
    private Boolean hasDraft;

    @Schema(description = "草稿引用的模型显示名（无草稿时为当前默认版本引用的模型，服务端补充）", example = "GPT-4o 主力")
    private String modelName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
