package com.finance.advisor.api.controller;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.rag.DocumentIngestionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 对话文件上传接口 - 临时解析文件内容用于 AI 对话上下文（不写入向量库）
 */
@RestController
@RequestMapping("/api/chat")
public class ChatFileController {

    private static final Logger log = LoggerFactory.getLogger(ChatFileController.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "xlsx", "xls", "csv", "md", "txt", "png", "jpg", "jpeg"
    );

    private final DocumentIngestionService ingestionService;

    public ChatFileController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/files")
    public ApiResponse<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "文件大小超过10MB限制");
        }

        String filename = file.getOriginalFilename();
        String extension = getFileExtension(filename);

        if (!SUPPORTED_EXTENSIONS.contains(extension.toLowerCase())) {
            return ApiResponse.error(400, "不支持的文件格式: " + extension);
        }

        try {
            // 解析文件为文本（不写入向量库）
            String textContent = ingestionService.parseFileToText(file);

            String fileId = UUID.randomUUID().toString().substring(0, 8);

            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("fileName", filename);
            response.put("fileType", extension);
            response.put("fileSize", file.getSize());
            // 截断过长的文本内容，避免请求体过大
            String truncated = textContent.length() > 50000
                    ? textContent.substring(0, 50000) + "\n...(内容过长已截断)"
                    : textContent;
            response.put("textContent", truncated);

            log.info("对话文件上传成功: filename={}, size={}, textLength={}",
                    filename, file.getSize(), textContent.length());

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("对话文件上传失败: {}", filename, e);
            return ApiResponse.error(500, "文件解析失败: " + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
