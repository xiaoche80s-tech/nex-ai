package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 智能体规格发布命令：当前草稿固化为不可变新版本")
@Data
public class AgentSpecPublishCommand {

    @Schema(description = "规格编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "规格编号不能为空")
    private Long id;

    @Schema(description = "发布说明", example = "调整提示词与温度")
    @Size(max = 255, message = "发布说明不能超过 255 个字符")
    private String remark;

}
