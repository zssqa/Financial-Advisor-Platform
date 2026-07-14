package com.finance.advisor.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多格式文档导入服务
 *
 * 支持: PDF, Excel (.xlsx/.xls), CSV, Markdown (.md), 纯文本 (.txt), 图片 (jpg/png)
 * 流程: 上传文档 -> 格式识别 -> 读取内容 -> 文档分割(chunk) -> 向量化存储
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final DocumentMetadataService documentMetadataService;

    public DocumentIngestionService(VectorStore vectorStore, DocumentMetadataService documentMetadataService) {
        this.vectorStore = vectorStore;
        this.documentMetadataService = documentMetadataService;
    }

    public int ingestDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String ext = getExtension(filename);
        DocumentReader reader = createReader(ext, file);

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(1000)
                .build();
        List<Document> chunks = splitter.apply(reader.get());

        chunks.forEach(doc -> {
            doc.getMetadata().put("source", filename);
            doc.getMetadata().put("format", ext);
            doc.getMetadata().put("upload_time", LocalDateTime.now().toString());
            doc.getMetadata().put("file_size", file.getSize());
        });

        vectorStore.add(chunks);
        log.info("文档摄入完成: filename={}, chunks={}", filename, chunks.size());
        return chunks.size();
    }

    /**
     * 将网页内容摄入知识库（用于联网搜索结果沉淀）
     * @param title 标题
     * @param content 内容
     * @param sourceUrl 来源URL
     * @param publishedDate 发布日期（可为null）
     * @return 摄入的分块数
     */
    public int ingestWebContent(String title, String content, String sourceUrl, String publishedDate) {
        if (content == null || content.isBlank()) return 0;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", sourceUrl != null ? sourceUrl : title);
        metadata.put("title", title);
        metadata.put("format", "web");
        metadata.put("source_type", "web");
        metadata.put("source_url", sourceUrl);
        if (publishedDate != null) {
            metadata.put("published_date", publishedDate);
        }
        metadata.put("fetch_time", String.valueOf(System.currentTimeMillis()));

        Document doc = new Document(content, metadata);

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(1000)
                .build();
        List<Document> chunks = splitter.apply(List.of(doc));

        vectorStore.add(chunks);

        // 写入 knowledge_documents 元数据表，使 existsByUrl 去重链路闭合
        String docId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime pubDate = null;
        if (publishedDate != null && !publishedDate.isBlank()) {
            try {
                pubDate = LocalDateTime.parse(publishedDate, java.time.format.DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e) {
                try {
                    pubDate = LocalDateTime.parse(publishedDate + "T00:00:00");
                } catch (Exception ignored) {}
            }
        }
        documentMetadataService.saveDocumentMetadata(
                docId,
                title != null ? title : (sourceUrl != null ? sourceUrl : "web_content"),
                "web",
                content.length(),
                "internet",
                chunks.size(),
                sourceUrl,
                pubDate,
                "web"
        );

        log.info("网页内容摄入完成: title={}, url={}, chunks={}", title, sourceUrl, chunks.size());
        return chunks.size();
    }

    private DocumentReader createReader(String ext, MultipartFile file) throws IOException {
        return switch (ext.toLowerCase()) {
            case "pdf" -> new PagePdfDocumentReader(new InputStreamResource(file.getInputStream()));
            case "xlsx", "xls" -> new com.finance.advisor.rag.reader.ExcelDocumentReader(new InputStreamResource(file.getInputStream()));
            case "csv" -> new com.finance.advisor.rag.reader.CsvDocumentReader(new InputStreamResource(file.getInputStream()));
            case "md" -> new com.finance.advisor.rag.reader.MarkdownDocumentReader(new InputStreamResource(file.getInputStream()));
            case "txt" -> {
                TextReader reader = new TextReader(new InputStreamResource(file.getInputStream()));
                reader.getCustomMetadata().put("source", file.getOriginalFilename());
                yield reader;
            }
            case "png", "jpg", "jpeg" -> new com.finance.advisor.rag.reader.OcrDocumentReader(new InputStreamResource(file.getInputStream()));
            default -> throw new IllegalArgumentException("不支持的文件格式: " + ext);
        };
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1) return "";
        return filename.substring(dot + 1);
    }

    public void ingestPdf(MultipartFile file) throws IOException {
        ingestDocument(file);
    }

    /**
     * 解析文件为文本（不写入向量库），用于对话临时文件上传
     */
    public String parseFileToText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String ext = getExtension(filename);
        DocumentReader reader = createReader(ext, file);
        List<Document> documents = reader.get();
        StringBuilder sb = new StringBuilder();
        for (Document doc : documents) {
            sb.append(doc.getText()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
