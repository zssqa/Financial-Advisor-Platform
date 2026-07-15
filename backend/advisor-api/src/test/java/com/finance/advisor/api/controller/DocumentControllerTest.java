package com.finance.advisor.api.controller;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.rag.DocumentIngestionService;
import com.finance.advisor.rag.DocumentMetadataService;
import com.finance.advisor.tool.FinancialTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentController 单元测试：使用纯 Mockito 直接调用控制器方法，
 * 覆盖上传/列表/搜索/分类/统计/删除/网络摄入等接口的分支逻辑。
 */
class DocumentControllerTest {

    private DocumentIngestionService ingestionService;
    private DocumentMetadataService metadataService;
    private FinancialTools financialTools;
    private DocumentController controller;

    @BeforeEach
    void setUp() {
        ingestionService = mock(DocumentIngestionService.class);
        metadataService = mock(DocumentMetadataService.class);
        financialTools = mock(FinancialTools.class);
        controller = new DocumentController(ingestionService, metadataService, financialTools);
    }

    @Test
    void upload_success_returnsSuccessAndPersistsMetadata() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.pdf");
        when(file.getSize()).thenReturn(1024L);
        when(ingestionService.ingestDocument(file)).thenReturn(5);

        ApiResponse<Map<String, Object>> response = controller.uploadDocument(file, "general");

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(true, response.getData().get("success"));
        assertEquals("test.pdf", response.getData().get("filename"));
        verify(ingestionService).ingestDocument(file);
        verify(metadataService).saveDocumentMetadata(anyString(), eq("test.pdf"), eq("pdf"),
                eq(1024L), eq("general"), eq(5));
    }

    @Test
    void upload_emptyFile_returns400() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        ApiResponse<Map<String, Object>> response = controller.uploadDocument(file, "general");

        assertEquals(400, response.getCode());
    }

    @Test
    void upload_unsupportedExtension_returns400() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.exe");

        ApiResponse<Map<String, Object>> response = controller.uploadDocument(file, "general");

        assertEquals(400, response.getCode());
    }

    @Test
    void list_returnsAllDocuments() {
        Map<String, Object> doc1 = Map.of("id", "d1", "filename", "a.pdf");
        Map<String, Object> doc2 = Map.of("id", "d2", "filename", "b.txt");
        when(metadataService.getAllDocuments()).thenReturn(List.of(doc1, doc2));

        ApiResponse<List<Map<String, Object>>> response = controller.listDocuments();

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(2, response.getData().size());
    }

    @Test
    void search_withKeyword_returnsMatchedDocs() {
        Map<String, Object> doc = Map.of("id", "d1", "filename", "bank.pdf");
        when(metadataService.searchDocuments("bank")).thenReturn(List.of(doc));

        ApiResponse<List<Map<String, Object>>> response = controller.searchDocuments("bank");

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
    }

    @Test
    void queryByCategory_returnsDocs() {
        Map<String, Object> doc = Map.of("id", "d1", "category", "report");
        when(metadataService.queryByCategory("report")).thenReturn(List.of(doc));

        ApiResponse<List<Map<String, Object>>> response = controller.queryByCategory("report");

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
    }

    @Test
    void getStatistics_returnsStats() {
        Map<String, Object> stats = Map.of("total_documents", 3);
        when(metadataService.getStatistics()).thenReturn(stats);

        ApiResponse<Map<String, Object>> response = controller.getStatistics();

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(3, response.getData().get("total_documents"));
    }

    @Test
    void delete_existing_returns200() {
        when(metadataService.deleteDocument("doc1")).thenReturn(true);

        ApiResponse<Map<String, Object>> response = controller.deleteDocument("doc1");

        assertEquals(200, response.getCode());
    }

    @Test
    void delete_nonExisting_returns404() {
        when(metadataService.deleteDocument("missing")).thenReturn(false);

        ApiResponse<Map<String, Object>> response = controller.deleteDocument("missing");

        assertEquals(404, response.getCode());
    }

    @Test
    void ingestFromWeb_success_returns200() {
        when(financialTools.searchFinanceNews("银行")).thenReturn("some news text");

        ApiResponse<Map<String, Object>> response =
                controller.ingestFromWeb(Map.of("keyword", "银行"));

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals(true, response.getData().get("success"));
    }

    @Test
    void ingestFromWeb_blankKeyword_returns400() {
        ApiResponse<Map<String, Object>> response =
                controller.ingestFromWeb(Map.of("keyword", ""));

        assertEquals(400, response.getCode());
    }
}
