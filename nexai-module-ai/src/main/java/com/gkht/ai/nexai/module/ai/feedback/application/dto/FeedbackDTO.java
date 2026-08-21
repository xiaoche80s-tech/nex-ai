package com.gkht.ai.nexai.module.ai.feedback.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 问题反馈 DTO")
@Data
public class FeedbackDTO {

    @Schema(description = "反馈编号", example = "1")
    private Long id;

    @Schema(description = "反馈内容", example = "调试台回复出现乱码")
    private String content;

    @Schema(description = "截图 URL 列表")
    private List<String> screenshotUrls;

    @Schema(description = "关联会话标识", example = "debug-session-001")
    private String sessionId;

    @Schema(description = "处理状态编码（10 待处理 / 20 处理中 / 30 已解决 / 40 已关闭）", example = "10")
    private Integer status;

    @Schema(description = "提交人用户编号", example = "1")
    private Long submitterId;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

}
