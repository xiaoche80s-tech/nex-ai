package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 智能体规格详情 DTO：含草稿与当前默认版本快照")
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentSpecDetailDTO extends AgentSpecDTO {

    @Schema(description = "草稿配置，无草稿为 null（编辑表单按此预填）")
    private AgentSpecConfigDTO draft;

    @Schema(description = "当前默认版本快照，从未发布为 null（无草稿时编辑表单按此预填）")
    private AgentSpecVersionDTO currentVersion;

}
