package com.finance.advisor.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 *
 * 结合向量语义检索 + PostgreSQL 全文关键词检索，
 * 使用 Reciprocal Rank Fusion (RRF) 算法融合排序结果。
 */
@Service
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HybridSearchService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Document> hybridSearch(String query, int topK) {
        List<Document> vectorDocs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK * 2).build());

        // 关键词检索：基于 pgvector 的 vector_store 表 content 字段进行 ILIKE 模糊匹配
        List<Document> keywordDocs = jdbcTemplate.query(
                "SELECT content, metadata FROM vector_store WHERE content ILIKE '%' || ? || '%' LIMIT ?",
                (rs, rowNum) -> {
                    String content = rs.getString("content");
                    String metadataJson = rs.getString("metadata");
                    Map<String, Object> metadata = new HashMap<>();
                    if (metadataJson != null) {
                        try {
                            metadata = objectMapper.readValue(metadataJson, Map.class);
                        } catch (Exception ignored) {
                        }
                    }
                    return new Document(content, metadata);
                },
                query, topK);

        return rankFusion(vectorDocs, keywordDocs, topK);
    }

    private List<Document> rankFusion(
            List<Document> vectorDocs,
            List<Document> keywordDocs,
            int topK) {

        Map<String, Document> docMap = new LinkedHashMap<>();
        Map<String, Double> scoreMap = new HashMap<>();

        int k = 60;
        for (int i = 0; i < vectorDocs.size(); i++) {
            Document doc = vectorDocs.get(i);
            String id = doc.getId() != null ? doc.getId() : doc.getText().hashCode() + "";
            docMap.putIfAbsent(id, doc);
            scoreMap.merge(id, 1.0 / (k + i), Double::sum);
        }

        for (int i = 0; i < keywordDocs.size(); i++) {
            Document doc = keywordDocs.get(i);
            String id = doc.getId() != null ? doc.getId() : doc.getText().hashCode() + "";
            docMap.putIfAbsent(id, doc);
            scoreMap.merge(id, 1.0 / (k + i), Double::sum);
        }

        return docMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        scoreMap.getOrDefault(b.getKey(), 0.0),
                        scoreMap.getOrDefault(a.getKey(), 0.0)))
                .limit(topK)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
}
