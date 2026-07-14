package com.finance.advisor.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DocumentIngestionService 集成测试
 */
class DocumentIngestionIntegrationTest {

    @Test
    void testIngestionServiceCreation() {
        VectorStore mockStore = mock(VectorStore.class);
        DocumentMetadataService mockMetadataService = mock(DocumentMetadataService.class);
        DocumentIngestionService service = new DocumentIngestionService(mockStore, mockMetadataService);
        assertNotNull(service);
    }
}
