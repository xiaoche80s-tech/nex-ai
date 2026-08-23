package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 智能体规格发布命令：把当前草稿固化为不可变版本快照并推进当前版本指针。
 * 发布校验（模型引用/挂载引用非空）在领域层完成，此处只做输入边界。
 */
@Schema(description = "管理后台 - 智能体规格发布命令")
@Data
public class AgentSpecPublishCommand {

    @Schema(description = "规格编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "规格编号不能为空")
    private Long id;

    @Schema(description = "发布备注", example = "首版发布")
    @Size(max = 255, message = "发布备注不能超过 255 个字符")
    private String note;

}
