package com.gkht.ai.nexai.module.ai.channel.application.dto;

import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ConnectivityResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 连通性探测结果 DTO。
 */
@Schema(description = "管理后台 - 渠道连通性探测结果 DTO")
@Data
public class ConnectivityTestDTO {

    @Schema(description = "是否连通", example = "true")
    private Boolean success;

    @Schema(description = "探测耗时（毫秒）", example = "812")
    private Long durationMs;

    @Schema(description = "说明（失败时携带根因，如鉴权失败/超时/模型名错误）", example = "连通正常（响应 ID：chatcmpl-xxx）")
    private String message;

    /**
     * 由探测结果值对象转 DTO
     */
    public static ConnectivityTestDTO from(ConnectivityResult result) {
        ConnectivityTestDTO dto = new ConnectivityTestDTO();
        dto.setSuccess(result.success());
        dto.setDurationMs(result.durationMs());
        dto.setMessage(result.message());
        return dto;
    }

}
