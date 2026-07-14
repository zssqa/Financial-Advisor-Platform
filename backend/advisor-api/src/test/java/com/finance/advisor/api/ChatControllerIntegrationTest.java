package com.finance.advisor.api;

import com.finance.advisor.agent.core.FinancialAdvisorAgent;
import com.finance.advisor.api.controller.ChatController;
import com.finance.advisor.api.dto.ChatRequest;
import com.finance.advisor.user.UserService;
import com.alibaba.cloud.ai.graph.NodeOutput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ChatController 集成测试
 */
class ChatControllerIntegrationTest {

    @Test
    void testChatRequestDto() {
        ChatRequest request = new ChatRequest();
        request.setMessage("test message");
        request.setConversationId("conv-1");

        assertEquals("test message", request.getMessage());
        assertEquals("conv-1", request.getConversationId());
    }

    @Test
    void testChatRequestFileInfo() {
        ChatRequest.FileInfo fileInfo = new ChatRequest.FileInfo();
        fileInfo.setName("test.pdf");
        fileInfo.setType("pdf");

        assertEquals("test.pdf", fileInfo.getName());
        assertEquals("pdf", fileInfo.getType());
    }

    @Test
    void streamChat_returnsSseEvents() {
        FinancialAdvisorAgent mockAgent = mock(FinancialAdvisorAgent.class);
        UserService mockUserService = mock(UserService.class);
        when(mockAgent.stream(anyString())).thenReturn(Flux.<NodeOutput>empty());
        ChatController controller = new ChatController(mockAgent, mockUserService);

        ChatRequest request = new ChatRequest();
        request.setMessage("test");

        StepVerifier.create(controller.streamChat(request))
            .expectNext("[DONE]")
            .verifyComplete();
    }

    @Test
    void streamChat_emptyMessageThrowsException() {
        FinancialAdvisorAgent mockAgent = mock(FinancialAdvisorAgent.class);
        UserService mockUserService = mock(UserService.class);
        ChatController controller = new ChatController(mockAgent, mockUserService);

        ChatRequest emptyRequest = new ChatRequest();
        emptyRequest.setMessage("");

        assertThrows(ResponseStatusException.class, () -> controller.streamChat(emptyRequest));

        verify(mockAgent, never()).stream(anyString());
    }
}
