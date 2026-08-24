package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 规格私有文件夹文件上传凭证 DTO（工单 18）：上传成功返回存储地址 +
 * 内容哈希 + 字节数，前端把凭证填进规格表单的文件夹清单（folders 通道），
 * 随规格草稿保存、发布时随版本快照固化。
 */
@Schema(description = "管理后台 - 规格私有文件夹文件上传凭证")
@Data
public class FolderFileUploadDTO {

    @Schema(description = "存储地址（infra FileApi 返回的访问路径）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(description = "内容 SHA-256（内容寻址，装配物化比对依据）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String contentHash;

    @Schema(description = "文件字节数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long size;

}
