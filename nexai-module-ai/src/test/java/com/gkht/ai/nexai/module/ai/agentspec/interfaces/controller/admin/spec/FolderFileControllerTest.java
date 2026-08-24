package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.FolderFileUploadDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.FolderFileServiceImpl;
import com.gkht.ai.nexai.module.ai.shared.util.Hashes;
import com.gkht.ai.nexai.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_FOLDER_FILE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 规格私有文件夹上传契约测试（工单 18，H2 上下文 + FileApi mock）：断言上传接口
 * 返回凭证三元组（url / contentHash / size）——哈希由服务端计算（内容寻址不可前端自报），
 * 文件实体经 infra FileApi 落存储。
 */
@Import({FolderFileController.class, FolderFileServiceImpl.class,
        FolderFileControllerTest.MockitoBeansConfiguration.class})
public class FolderFileControllerTest extends BaseDbUnitTest {

    @Resource
    private FolderFileController folderFileController;

    @Resource
    private FileApi fileApi;

    @TestConfiguration
    static class MockitoBeansConfiguration {

        @org.springframework.context.annotation.Bean
        public FileApi fileApi() {
            return Mockito.mock(FileApi.class);
        }

    }

    @Test
    @DisplayName("上传：返回凭证三元组，哈希为内容 SHA-256、字节数为内容长度")
    public void uploadReturnsCredential() throws Exception {
        byte[] content = "参考资料内容".getBytes(StandardCharsets.UTF_8);
        Mockito.when(fileApi.createFile(Mockito.any(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString())).thenReturn("http://files/faq.md");

        com.gkht.ai.nexai.module.ai.agentspec.application.command.FolderFileUploadCommand command =
                new com.gkht.ai.nexai.module.ai.agentspec.application.command.FolderFileUploadCommand();
        command.setFile(new MockMultipartFile("file", "faq.md", "text/markdown", content));
        FolderFileUploadDTO dto = folderFileController.uploadFile(command).getData();

        assertNotNull(dto);
        assertEquals("http://files/faq.md", dto.getUrl());
        assertEquals(Hashes.sha256Hex(content), dto.getContentHash());
        assertEquals(content.length, dto.getSize());
        Mockito.verify(fileApi).createFile(Mockito.any(), Mockito.eq("faq.md"),
                Mockito.eq("ai/spec-folder"), Mockito.eq("text/markdown"));
    }

    @Test
    @DisplayName("上传空内容被拒绝（零字节文件不签发凭证，业务错误码）")
    public void uploadRejectsEmptyContent() {
        com.gkht.ai.nexai.module.ai.agentspec.application.command.FolderFileUploadCommand command =
                new com.gkht.ai.nexai.module.ai.agentspec.application.command.FolderFileUploadCommand();
        command.setFile(new MockMultipartFile("file", "empty.md", "text/markdown", new byte[0]));
        assertServiceException(() -> folderFileController.uploadFile(command),
                AGENT_SPEC_FOLDER_FILE_INVALID, "上传的文件内容不能为空");
    }

}
