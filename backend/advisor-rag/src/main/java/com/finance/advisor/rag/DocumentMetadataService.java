package com.finance.advisor.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档元数据管理服务
 */
@Service
public class DocumentMetadataService {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataService.class);

    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public DocumentMetadataService(JdbcTemplate jdbcTemplate, VectorStore vectorStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        initializeSchema();
    }

    public void initializeSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS knowledge_documents (
                id VARCHAR(64) PRIMARY KEY,
                filename VARCHAR(255) NOT NULL,
                file_type VARCHAR(20) NOT NULL,
                file_size BIGINT DEFAULT 0,
                category VARCHAR(50) DEFAULT 'general',
                status VARCHAR(20) DEFAULT 'processing',
                chunk_count INT DEFAULT 0,
                upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                uploader VARCHAR(100) DEFAULT 'system',
                description TEXT
            )
            """);
        // 升级表结构：新增来源相关字段（PostgreSQL 支持 ADD COLUMN IF NOT EXISTS）
        jdbcTemplate.execute("ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS source_url VARCHAR(2048)");
        jdbcTemplate.execute("ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS published_date TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) DEFAULT 'upload'");
    }

    public void saveDocumentMetadata(String id, String filename, String fileType,
                                      long fileSize, String category, int chunkCount) {
        // 向后兼容：委托给带新字段的重载方法，使用默认值
        saveDocumentMetadata(id, filename, fileType, fileSize, category, chunkCount,
                null, null, "upload");
    }

    public void saveDocumentMetadata(String id, String filename, String fileType,
                                      long fileSize, String category, int chunkCount,
                                      String sourceUrl, LocalDateTime publishedDate, String sourceType) {
        String sql = """
            INSERT INTO knowledge_documents (id, filename, file_type, file_size, category, status, chunk_count, upload_time, source_url, published_date, source_type)
            VALUES (?, ?, ?, ?, ?, 'indexed', ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                status = 'indexed', chunk_count = ?, upload_time = CURRENT_TIMESTAMP,
                source_url = COALESCE(EXCLUDED.source_url, knowledge_documents.source_url),
                published_date = COALESCE(EXCLUDED.published_date, knowledge_documents.published_date),
                source_type = COALESCE(EXCLUDED.source_type, knowledge_documents.source_type)
            """;
        Timestamp publishedTs = publishedDate != null ? Timestamp.valueOf(publishedDate) : null;
        jdbcTemplate.update(sql, id, filename, fileType, fileSize,
                category, chunkCount, Timestamp.valueOf(LocalDateTime.now()),
                sourceUrl, publishedTs, sourceType, chunkCount);
    }

    public boolean existsByUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_documents WHERE source_url = ?",
                Integer.class, url);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("检查URL存在性失败: {}", e.getMessage());
            return false;
        }
    }

    public void updateStatus(String id, String status) {
        jdbcTemplate.update("UPDATE knowledge_documents SET status = ? WHERE id = ?", status, id);
    }

    public boolean deleteDocument(String id) {
        int deleted = jdbcTemplate.update("DELETE FROM knowledge_documents WHERE id = ?", id);
        vectorStore.delete(List.of(id));
        return deleted > 0;
    }

    public List<Map<String, Object>> queryByCategory(String category) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM knowledge_documents WHERE category = ? ORDER BY upload_time DESC", category);
    }

    public List<Map<String, Object>> searchDocuments(String keyword) {
        String sql = """
            SELECT * FROM knowledge_documents
            WHERE filename ILIKE ? OR description ILIKE ?
            ORDER BY upload_time DESC LIMIT 50
            """;
        String pattern = "%" + keyword + "%";
        return jdbcTemplate.queryForList(sql, pattern, pattern);
    }

    public List<Map<String, Object>> getAllDocuments() {
        return jdbcTemplate.queryForList("SELECT * FROM knowledge_documents ORDER BY upload_time DESC");
    }

    public Map<String, Object> getStatistics() {
        List<Map<String, Object>> typeStats = jdbcTemplate.queryForList(
                "SELECT file_type, COUNT(*) as count, SUM(file_size) as total_size "
                + "FROM knowledge_documents GROUP BY file_type");
        long totalDocs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_documents", Long.class);
        Map<String, Object> result = new HashMap<>();
        result.put("total_documents", totalDocs);
        result.put("type_statistics", typeStats);
        return result;
    }
}
