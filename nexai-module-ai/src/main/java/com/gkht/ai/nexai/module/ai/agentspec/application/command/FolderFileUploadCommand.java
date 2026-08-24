package com.gkht.ai.nexai.module.ai.agentspec.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理后台 - 规格私有文件夹文件上传命令（工单 18）：文件实体经 infra FileApi 落存储，
 * 服务端计算内容 SHA-256 后返回上传凭证（url + contentHash + size）。
 */
@Schema(description = "管理后台 - 规格私有文件夹文件上传命令")
@Data
public class FolderFileUploadCommand {

    @Schema(description = "文件附件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件不能为空")
    private MultipartFile file;

}
