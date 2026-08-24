package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.FolderFileUploadCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.FolderFileUploadDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.FolderFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。
 */
@Tag(name = "管理后台 - 规格私有文件夹文件")
@RestController
@RequestMapping("/ai/spec/folder-file")
@Validated
public class FolderFileController {

    @Resource
    private FolderFileService folderFileService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件夹内单个文件",
            description = "文件实体经 infra FileApi 落存储，服务端计算内容 SHA-256，返回上传凭证"
                    + "（url + contentHash + size）；前端把凭证填入规格表单的 folders 清单，发布时随版本快照固化")
    @Parameter(name = "file", description = "文件附件", required = true,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary"))
    @PreAuthorize("@ss.hasPermission('ai:spec:create')")
    public CommonResult<FolderFileUploadDTO> uploadFile(@Valid FolderFileUploadCommand command)
            throws IOException {
        return success(folderFileService.upload(
                command.getFile().getInputStream().readAllBytes(),
                command.getFile().getOriginalFilename(),
                command.getFile().getContentType()));
    }

}
