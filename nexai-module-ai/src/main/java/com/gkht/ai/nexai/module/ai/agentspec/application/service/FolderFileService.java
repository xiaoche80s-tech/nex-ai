package com.gkht.ai.nexai.module.ai.agentspec.application.service;

import com.gkht.ai.nexai.module.ai.agentspec.application.dto.FolderFileUploadDTO;

/**
 * 规格私有文件夹文件应用服务端口（工单 18）。
 */
public interface FolderFileService {

    /**
     * 上传文件夹内单个文件，签发凭证（url + contentHash + size）
     *
     * @param content 文件内容字节
     * @param name    文件名（保留原名入存储，路径唯一性由 FileApi 生成）
     * @param type    MIME 类型，可空（FileApi 兜底推断）
     * @return 上传凭证
     */
    FolderFileUploadDTO upload(byte[] content, String name, String type);

}
