package com.finance.advisor.api.dto;

import java.util.List;

/**
 * 对话响应 DTO
 */
public class ChatResponse {

    private String response;
    private String sessionId;
    private List<String> toolsCalled;

    public ChatResponse() {}

    public ChatResponse(String response, String sessionId, List<String> toolsCalled) {
        this.response = response;
        this.sessionId = sessionId;
        this.toolsCalled = toolsCalled;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<String> getToolsCalled() {
        return toolsCalled;
    }

    public void setToolsCalled(List<String> toolsCalled) {
        this.toolsCalled = toolsCalled;
    }
}
