package com.finance.advisor.api.controller;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.rag.DocumentIngestionService;
import com.finance.advisor.rag.DocumentMetadataService;
import com.finance.advisor.tool.FinancialTools;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档管理接口 - 上传/删除/搜索/分类/统计
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "xlsx", "xls", "csv", "md", "txt", "png", "jpg", "jpeg");

    private final DocumentIngestionService ingestionService;
    private final DocumentMetadataService metadataService;
    private final FinancialTools financialTools;

    public DocumentController(DocumentIngestionService ingestionService,
                              DocumentMetadataService metadataService,
                              FinancialTools financialTools) {
        this.ingestionService = ingestionService;
        this.metadataService = metadataService;
        this.financialTools = financialTools;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ApiResponse.error(400, "文件名无效");
        }

        String ext = getExtension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(ext.toLowerCase())) {
            return ApiResponse.error(400, "不支持的文件格式: " + ext + "，支持的格式: PDF, Excel, CSV, Markdown, TXT, PNG, JPG");
        }

        try {
            String docId = UUID.randomUUID().toString().substring(0, 8);
            int chunkCount = ingestionService.ingestDocument(file);
            metadataService.saveDocumentMetadata(docId, filename, ext, file.getSize(), category, chunkCount);

            return ApiResponse.success(Map.of(
                    "success", true,
                    "message", "\"" + filename + "\" 导入知识库成功",
                    "id", docId,
                    "filename", filename,
                    "format", ext,
                    "category", category,
                    "size", file.getSize(),
                    "chunk_count", chunkCount));
        } catch (IOException e) {
            return ApiResponse.error(500, "文件导入失败: " + e.getMessage());
        }
    }

    @PostMapping(":ingest-web")
    public ApiResponse<Map<String, Object>> ingestFromWeb(@RequestBody Map<String, String> request) {
        String keyword = request.get("keyword");
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.error(400, "keyword is required");
        }
        // 委托给 FinancialTools.searchFinanceNews，该方法会自动将搜索结果摄入知识库
        String result = financialTools.searchFinanceNews(keyword);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("keyword", keyword);
        response.put("result", result);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteDocument(@PathVariable String id) {
        boolean deleted = metadataService.deleteDocument(id);
        if (deleted) {
            return ApiResponse.success(Map.of("message", "文档已删除"));
        }
        return ApiResponse.error(404, "文档不存在");
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listDocuments() {
        return ApiResponse.success(metadataService.getAllDocuments());
    }

    @GetMapping(params = "keyword")
    public ApiResponse<List<Map<String, Object>>> searchDocuments(
            @RequestParam("keyword") String keyword) {
        return ApiResponse.success(metadataService.searchDocuments(keyword));
    }

    @GetMapping("/category/{category}")
    public ApiResponse<List<Map<String, Object>>> queryByCategory(@PathVariable String category) {
        return ApiResponse.success(metadataService.queryByCategory(category));
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        return ApiResponse.success(metadataService.getStatistics());
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1) return "";
        return filename.substring(dot + 1);
    }
}
