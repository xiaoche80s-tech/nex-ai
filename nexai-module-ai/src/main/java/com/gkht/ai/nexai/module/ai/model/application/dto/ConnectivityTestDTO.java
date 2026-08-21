package com.gkht.ai.nexai.module.ai.model.application.dto;

import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ConnectivityResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 连通性测试结果 DTO")
@Data
public class ConnectivityTestDTO {

    @Schema(description = "是否连通", example = "true")
    private Boolean success;

    @Schema(description = "耗时（毫秒）", example = "860")
    private Long durationMs;

    @Schema(description = "说明信息：成功为响应摘要，失败为错误原因", example = "连通正常")
    private String message;

    /**
     * 从领域探测结果转换（模型级与渠道级探测共用）
     */
    public static ConnectivityTestDTO from(ConnectivityResult result) {
        ConnectivityTestDTO dto = new ConnectivityTestDTO();
        dto.setSuccess(result.success());
        dto.setDurationMs(result.durationMs());
        dto.setMessage(result.message());
        return dto;
    }

}
