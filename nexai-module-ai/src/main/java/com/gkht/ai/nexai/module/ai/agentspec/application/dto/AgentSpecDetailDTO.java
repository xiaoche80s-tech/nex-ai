package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 智能体规格详情 DTO（编辑面回填用）：主体元数据 + 配置平铺（草稿优先；已发布无草稿时
 * 取当前生效快照，ADR 0004），与创建/更新命令同构，前端表单可直接回填并原样回传。
 */
@Schema(description = "管理后台 - 智能体规格详情 DTO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSpecDetailDTO extends AgentSpecFlatConfigDTO {

    @Schema(description = "规格编号", example = "1")
    private Long id;

    @Schema(description = "规格名称", example = "客服助手")
    private String name;

    @Schema(description = "业务编码（创建后不可变）", example = "customer-service")
    private String specCode;

    @Schema(description = "归属层级", example = "TENANT")
    private String ownerLevel;

    @Schema(description = "归属用户编号（用户级 = 创建者），非用户级为 null")
    private Long ownerUserId;

    @Schema(description = "图标标识", example = "ep:service")
    private String icon;

    @Schema(description = "是否有草稿（发布清空草稿，编辑保存重建）")
    private Boolean hasDraft;

    @Schema(description = "当前生效版本号（当前版本指针），null = 从未发布")
    private Integer currentVersionNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
