package com.gkht.ai.nexai.module.ai.session.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调试会话 DTO（列表与详情共用）。rounds 轮次详情按需另查（消息历史恢复走
 * agentscope 状态槽位），本 DTO 只带元数据与状态。
 */
@Schema(description = "管理后台 - 调试会话 DTO")
@Data
public class SessionDTO {

    @Schema(description = "会话编号", example = "1")
    private Long id;

    @Schema(description = "会话业务键（agentscope 槽位 sessionId）", example = "dbg-xxx")
    private String sessionKey;

    @Schema(description = "标题", example = "渠道接入调试")
    private String title;

    @Schema(description = "会话类型（DEBUG/CHAT）", example = "DEBUG")
    private String type;

    @Schema(description = "会话状态（READY/ACTIVE/ASKING/CLOSED）", example = "ASKING")
    private String status;

    @Schema(description = "绑定规格编号", example = "1")
    private Long specId;

    @Schema(description = "绑定版本号（null = 当前版本）", example = "2")
    private Integer versionNo;

    @Schema(description = "规格业务编码（服务端补充展示用）", example = "data-agent")
    private String specCode;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
