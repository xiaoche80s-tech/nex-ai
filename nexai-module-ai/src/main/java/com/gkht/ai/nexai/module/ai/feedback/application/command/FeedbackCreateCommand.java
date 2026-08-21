package com.gkht.ai.nexai.module.ai.feedback.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 问题反馈提交命令")
@Data
public class FeedbackCreateCommand {

    @Schema(description = "反馈内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "调试台回复出现乱码")
    @NotEmpty(message = "反馈内容不能为空")
    @Size(max = 2048, message = "反馈内容不能超过 2048 个字符")
    private String content;

    @Schema(description = "截图 URL 列表，文件本体经 infra 文件服务上传（/infra/file/upload）", example = "[\"http://127.0.0.1:48080/admin-api/infra/file/get/f1.png\"]")
    @Size(max = 5, message = "截图最多 5 张")
    private List<@Size(max = 512, message = "截图 URL 长度不能超过 512") String> screenshotUrls;

    @Schema(description = "关联会话标识，可空", example = "debug-session-001")
    @Size(max = 64, message = "会话标识不能超过 64 个字符")
    private String sessionId;

}
