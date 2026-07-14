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
 * CSV 文档读取器
 */
public class CsvDocumentReader implements DocumentReader {

    private final Resource resource;

    public CsvDocumentReader(Resource resource) {
        this.resource = resource;
    }

    @Override
    public List<Document> get() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> {
                        Document doc = new Document(line);
                        doc.getMetadata().put("source", resource.getFilename());
                        doc.getMetadata().put("format", "csv");
                        if (header != null) {
                            doc.getMetadata().put("header", header);
                        }
                        return doc;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("读取 CSV 文件失败: " + e.getMessage(), e);
        }
    }
}
