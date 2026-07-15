package com.finance.advisor.api.controller;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.rag.DocumentIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChatFileController 单元测试：使用纯 Mockito 直接调用 uploadFile 方法，
 * 覆盖文件上传的空文件/超限/不支持格式/解析异常/成功等分支。
 */
class ChatFileControllerTest {

    private DocumentIngestionService ingestionService;
    private ChatFileController controller;

    @BeforeEach
    void setUp() {
        ingestionService = mock(DocumentIngestionService.class);
        controller = new ChatFileController(ingestionService);
    }

    @Test
    void upload_success_returnsFileIdAndContent() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "text content".getBytes());
        when(ingestionService.parseFileToText(file)).thenReturn("text content");

        ApiResponse<Map<String, Object>> response = controller.uploadFile(file);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertNotNull(response.getData().get("fileId"));
        assertEquals("test.txt", response.getData().get("fileName"));
        assertEquals("text content", response.getData().get("textContent"));
    }

    @Test
    void upload_emptyFile_returns400() {
        MultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[0]);

        ApiResponse<Map<String, Object>> response = controller.uploadFile(file);

        assertEquals(400, response.getCode());
    }

    @Test
    void upload_fileTooLarge_returns400() {
        MultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", new byte[11 * 1024 * 1024]);

        ApiResponse<Map<String, Object>> response = controller.uploadFile(file);

        assertEquals(400, response.getCode());
    }

    @Test
    void upload_unsupportedExtension_returns400() {
        MultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/octet-stream", "content".getBytes());

        ApiResponse<Map<String, Object>> response = controller.uploadFile(file);

        assertEquals(400, response.getCode());
    }

    @Test
    void upload_parseException_returns500() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes());
        when(ingestionService.parseFileToText(file)).thenThrow(new Exception("parse error"));

        ApiResponse<Map<String, Object>> response = controller.uploadFile(file);

        assertEquals(500, response.getCode());
    }
}
