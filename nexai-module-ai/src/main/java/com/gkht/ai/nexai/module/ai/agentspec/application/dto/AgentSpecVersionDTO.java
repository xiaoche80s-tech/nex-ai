package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体规格版本快照 DTO（列表项）：不可变快照的元信息。
 * 全量配置不随列表返回（快照可能很大），详情经 version-get 按需读取
 * （{@link AgentSpecVersionDetailDTO}，只读预览）。
 */
@Schema(description = "管理后台 - 智能体规格版本快照 DTO（列表项）")
@Data
public class AgentSpecVersionDTO {

    @Schema(description = "版本快照编号", example = "1")
    private Long id;

    @Schema(description = "版本号（规格内严格递增，1 起；发布后不可变，运行寻址用）", example = "1")
    private Integer versionNo;

    @Schema(description = "发布备注", example = "首版发布")
    private String note;

    @Schema(description = "是否为当前生效版本（当前版本指针判等）")
    private Boolean current;

    @Schema(description = "发布人编号（快照创建者）", example = "1")
    private Long creator;

    @Schema(description = "发布人昵称（经 AdminUserApi 解析；用户已删除时回退显示编号）", example = "芋道")
    private String publisherName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
