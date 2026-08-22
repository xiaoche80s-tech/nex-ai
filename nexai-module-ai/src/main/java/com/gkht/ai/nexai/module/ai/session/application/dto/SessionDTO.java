package com.gkht.ai.nexai.module.ai.session.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 会话 DTO")
@Data
public class SessionDTO {

    @Schema(description = "会话编号", example = "1")
    private Long id;

    @Schema(description = "会话标识（agentscope 状态存储寻址键，反馈关联用）", example = "dbg-0d1f2c3e-...")
    private String sessionKey;

    @Schema(description = "会话类型编码（10 调试 / 20 终端用户）", example = "10")
    private Integer type;

    @Schema(description = "绑定的规格编号", example = "1")
    private Long specId;

    @Schema(description = "绑定的规格版本号", example = "1")
    private Integer versionNo;

    @Schema(description = "会话标题", example = "客服智能体联调")
    private String title;

    @Schema(description = "已发送的消息轮数", example = "3")
    private Integer messageRounds;

    @Schema(description = "推理参数覆盖：最大迭代轮数（null 表示沿用版本快照）", example = "5")
    private Integer overrideMaxIters;

    @Schema(description = "推理参数覆盖：温度（null 表示沿用版本快照）", example = "0.9")
    private Double overrideTemperature;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
