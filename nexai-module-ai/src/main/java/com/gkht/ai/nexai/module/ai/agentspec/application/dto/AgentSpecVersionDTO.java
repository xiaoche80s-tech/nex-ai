package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 智能体规格版本 DTO（不可变快照）")
@Data
public class AgentSpecVersionDTO {

    @Schema(description = "版本记录编号", example = "1")
    private Long id;

    @Schema(description = "所属规格编号", example = "1")
    private Long specId;

    @Schema(description = "版本号", example = "1")
    private Integer versionNo;

    @Schema(description = "发布说明", example = "首个版本")
    private String remark;

    @Schema(description = "全量配置快照")
    private AgentSpecConfigDTO config;

    @Schema(description = "发布时间")
    private LocalDateTime createTime;

}
