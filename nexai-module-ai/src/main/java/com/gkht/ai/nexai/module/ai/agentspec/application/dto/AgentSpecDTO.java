package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体规格 DTO（列表项）。
 */
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

    @Schema(description = "描述（服务端从草稿 JSON 解析填充）", example = "回答客户咨询的智能客服")
    private String description;

    @Schema(description = "图标标识", example = "ep:service")
    private String icon;

    @Schema(description = "是否有草稿（发布后草稿保留为编辑底稿，不因发布而清除）")
    private Boolean hasDraft;

    @Schema(description = "当前生效版本号（null 表示从未发布，列表状态列渲染依据）", example = "3")
    private Integer currentVersionNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
