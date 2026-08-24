package com.gkht.ai.nexai.module.ai.agentspec.application.service;

import com.gkht.ai.nexai.module.ai.agentspec.application.dto.FolderFileUploadDTO;
import com.gkht.ai.nexai.module.ai.shared.util.Hashes;
import com.gkht.ai.nexai.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_FOLDER_FILE_INVALID;

/**
 * 规格私有文件夹文件应用服务（工单 18）：上传凭证签发——文件实体经 infra FileApi
 * （跨模块 api）落存储，服务端计算内容 SHA-256（内容寻址不可由前端自报），
 * 返回 url + contentHash + size 三元组供前端填入规格表单的文件夹清单。
 *
 * <p>上传的文件在发布前属草稿态（规格草稿持有清单）；发布时清单随版本快照固化
 * （不可变），装配物化时按 contentHash 比对。</p>
 */
@Service
public class FolderFileServiceImpl implements FolderFileService {

    /** 存储目录前缀（infra_file 的 directory 段，租户隔离由存储路径与访问权限共同保证） */
    private static final String DIRECTORY = "ai/spec-folder";

    @Resource
    private FileApi fileApi;

    @Override
    public FolderFileUploadDTO upload(byte[] content, String name, String type) {
        if (content == null || content.length == 0) {
            throw exception(AGENT_SPEC_FOLDER_FILE_INVALID, "上传的文件内容不能为空");
        }
        String url;
        try {
            url = fileApi.createFile(content, name, DIRECTORY, type);
        } catch (Exception ex) {
            throw exception(AGENT_SPEC_FOLDER_FILE_INVALID, "文件存储失败：" + ex.getMessage());
        }
        FolderFileUploadDTO dto = new FolderFileUploadDTO();
        dto.setUrl(url);
        dto.setContentHash(Hashes.sha256Hex(content));
        dto.setSize((long) content.length);
        return dto;
    }

}
