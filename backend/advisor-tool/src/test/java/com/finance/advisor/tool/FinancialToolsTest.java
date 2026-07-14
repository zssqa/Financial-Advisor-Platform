package com.finance.advisor.tool;

import com.finance.advisor.rag.DocumentIngestionService;
import com.finance.advisor.rag.DocumentMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FinancialTools 单元测试
 */
class FinancialToolsTest {

    private VectorStore mockVectorStore = mock(VectorStore.class);
    private DocumentIngestionService mockIngestionService = mock(DocumentIngestionService.class);
    private DocumentMetadataService mockMetadataService = mock(DocumentMetadataService.class);
    private FinancialTools tools = new FinancialTools(mockVectorStore, mockIngestionService, mockMetadataService);

    @Test
    void testCalculateCompoundInterest() {
        String result = tools.calculateCompoundInterest(10000, 5, 10, 12);
        assertNotNull(result);
        assertTrue(result.contains("16470.09")); // 预期本息合计
        assertTrue(result.contains("6470.09"));  // 预期收益
    }

    @Test
    void testCalculateLoanInterest() {
        String result = tools.calculateLoanInterest(1000000, 4.2, 360);
        assertNotNull(result);
        assertTrue(result.contains("月供"));
        assertTrue(result.contains("总利息"));
    }

    @Test
    void testSearchResearchReportsEmpty() {
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());
        String result = tools.searchResearchReports("test query");
        assertTrue(result.contains("未找到"));
    }

    @Test
    void testSearchResearchReportsWithResults() {
        Document doc = new Document("test content");
        doc.getMetadata().put("source", "test.pdf");
        when(mockVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));
        String result = tools.searchResearchReports("test query");
        assertTrue(result.contains("test content"));
    }
}
