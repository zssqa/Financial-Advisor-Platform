package com.finance.advisor.rag.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Markdown 文档读取器
 */
public class MarkdownDocumentReader implements DocumentReader {

    private final Resource resource;

    public MarkdownDocumentReader(Resource resource) {
        this.resource = resource;
    }

    @Override
    public List<Document> get() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String content = reader.lines().collect(Collectors.joining("\n"));
            String[] sections = content.split("(?=^## )");

            return java.util.Arrays.stream(sections)
                    .filter(s -> !s.isBlank())
                    .map(section -> {
                        Document doc = new Document(section.trim());
                        doc.getMetadata().put("source", resource.getFilename());
                        doc.getMetadata().put("format", "markdown");
                        return doc;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            try {
                String fullContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Document doc = new Document(fullContent);
                doc.getMetadata().put("source", resource.getFilename());
                doc.getMetadata().put("format", "markdown");
                return List.of(doc);
            } catch (Exception ex) {
                throw new RuntimeException("读取 Markdown 文件失败: " + e.getMessage(), e);
            }
        }
    }
}
