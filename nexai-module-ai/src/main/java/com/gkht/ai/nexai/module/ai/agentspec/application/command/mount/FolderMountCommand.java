package com.gkht.ai.nexai.module.ai.agentspec.application.command.mount;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderFile;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 规格私有文件夹挂载命令（工单 18 folders 通道）：
 * 类型 + 目标子目录名 + 文件清单（上传接口返回的凭证：url + 内容哈希 + 字节数）。
 */
@Schema(description = "管理后台 - 规格私有文件夹挂载命令")
@Data
public class FolderMountCommand {

    @Schema(description = "文件夹类型（ASSET = 资料文件夹，TOOLSET = 工具集文件夹）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "ASSET")
    @NotNull(message = "文件夹挂载必须声明类型")
    @Pattern(regexp = FolderType.REGEX,
            message = "文件夹类型仅支持 ASSET（资料）与 TOOLSET（工具集）")
    private String type;

    @Schema(description = "目标子目录名（物化到 workspace 的 knowledge/<name> 或 toolsets/<name>）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "product-faq")
    @NotBlank(message = "文件夹挂载必须声明目标子目录名")
    @Pattern(regexp = FolderMount.NAME_REGEX,
            message = "文件夹名须为字母或数字开头，仅含字母/数字/点/下划线/连字符（1~64 位）")
    private String name;

    @Schema(description = "文件清单（path 为文件夹内相对路径，凭证来自上传接口返回）")
    @NotNull(message = "文件夹挂载必须携带文件清单")
    @Size(max = FolderMount.FILES_MAX_SIZE,
            message = "单文件夹文件数不能超过 " + FolderMount.FILES_MAX_SIZE)
    @Valid
    private List<FolderFileCommand> files;

    @Schema(description = "管理后台 - 文件夹内单个文件条目（上传凭证）")
    @Data
    public static class FolderFileCommand {

        @Schema(description = "文件夹内相对路径（可含子目录）", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "faq.md")
        @NotBlank(message = "文件夹文件必须声明相对路径")
        @Size(max = FolderFile.PATH_MAX_LENGTH,
                message = "文件夹文件相对路径不能超过 " + FolderFile.PATH_MAX_LENGTH + " 个字符")
        private String path;

        @Schema(description = "存储地址（上传接口返回的 url）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "文件夹文件必须携带存储地址")
        @Size(max = FolderFile.URL_MAX_LENGTH,
                message = "文件夹文件存储地址不能超过 " + FolderFile.URL_MAX_LENGTH + " 个字符")
        private String url;

        @Schema(description = "内容 SHA-256（上传接口返回，内容寻址）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "文件夹文件必须携带内容哈希")
        @Pattern(regexp = FolderFile.CONTENT_HASH_REGEX,
                message = "内容哈希须为 64 位十六进制 SHA-256")
        private String contentHash;

        @Schema(description = "文件字节数", example = "1024")
        private Long size;
    }

}
