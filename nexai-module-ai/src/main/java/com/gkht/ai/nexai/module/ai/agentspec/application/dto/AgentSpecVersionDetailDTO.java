package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 智能体规格版本快照详情 DTO（只读预览，工单 24）：版本元信息 + 全量四层配置平铺
 * （与规格详情同构——快照与草稿配置同构，固化保真）。
 * 不含主体元数据（name/icon 不进版本快照，不展示）。
 */
@Schema(description = "管理后台 - 智能体规格版本快照详情 DTO（只读预览）")
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSpecVersionDetailDTO extends AgentSpecFlatConfigDTO {

    @Schema(description = "版本快照编号", example = "1")
    private Long id;

    @Schema(description = "版本号（规格内严格递增，1 起）", example = "1")
    private Integer versionNo;

    @Schema(description = "发布备注", example = "首版发布")
    private String note;

    @Schema(description = "是否为当前生效版本（当前版本指针判等）")
    private Boolean current;

    @Schema(description = "发布时间")
    private LocalDateTime createTime;

}
