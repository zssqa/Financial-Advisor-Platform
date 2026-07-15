package com.finance.advisor.api.controller;

import com.finance.advisor.agent.core.FinancialAdvisorAgent;
import com.finance.advisor.api.dto.ChatRequest;
import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.user.User;
import com.finance.advisor.user.UserService;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话接口 - 支持 SSE 流式输出，含工具调用事件推送
 *
 * 复用来源: spring-ai-alibaba-graph-core NodeOutput, OutputType, StreamingOutput
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final FinancialAdvisorAgent advisorAgent;
    private final UserService userService;

    public ChatController(FinancialAdvisorAgent advisorAgent, UserService userService) {
        this.advisorAgent = advisorAgent;
        this.userService = userService;
    }

    @PostMapping(value = "/messages:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        String message = request.getMessage();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is empty");
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Set<String> activeTools = ConcurrentHashMap.newKeySet();

        // 风险等级优先从 DB（User.riskLevel）读取，DB 读不到时回退到请求中的 riskLevel
        Long userId = currentUserId();
        String riskLevel = resolveRiskLevel(userId, request.getRiskLevel());

        // 拼接附件文本内容到消息中
        String enhancedMessage = message;
        List<ChatRequest.FileInfo> files = request.getFiles();
        if (files != null && !files.isEmpty()) {
            StringBuilder sb = new StringBuilder(message);
            for (ChatRequest.FileInfo file : files) {
                if (file.getTextContent() != null && !file.getTextContent().isBlank()) {
                    sb.append("\n\n[附件: ").append(file.getName()).append("]\n")
                            .append(file.getTextContent());
                }
            }
            enhancedMessage = sb.toString();
        }

        Flux<NodeOutput> agentFlux;
        if (riskLevel != null && !riskLevel.isBlank()) {
            agentFlux = (userId != null)
                    ? advisorAgent.stream(enhancedMessage, userId, riskLevel)
                    : advisorAgent.stream(enhancedMessage, riskLevel);
        } else {
            agentFlux = advisorAgent.stream(enhancedMessage);
        }

        agentFlux
                .doOnNext(output -> {
                    if (output instanceof StreamingOutput streaming) {
                        OutputType type = streaming.getOutputType();

                        if (type == OutputType.AGENT_MODEL_STREAMING) {
                            String chunk = streaming.chunk();
                            if (chunk != null && !chunk.isEmpty()) {
                                sink.tryEmitNext("{\"type\":\"message\",\"content\":\""
                                        + escapeJson(chunk) + "\"}");
                            }
                        } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                            String msg = streaming.chunk();
                            if (msg != null && !msg.isEmpty()) {
                                sink.tryEmitNext("{\"type\":\"message\",\"content\":\""
                                        + escapeJson(msg) + "\"}");
                            }
                        } else if (type == OutputType.AGENT_TOOL_STREAMING) {
                            String nodeName = streaming.node();
                            if (nodeName != null && activeTools.add(nodeName)) {
                                String toolName = extractToolName(nodeName);
                                sink.tryEmitNext("{\"type\":\"tool_call\",\"tool\":\""
                                        + escapeJson(toolName) + "\"}");
                            }
                        } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                            String nodeName = streaming.node();
                            if (nodeName != null) {
                                activeTools.remove(nodeName);
                                String toolName = extractToolName(nodeName);
                                String result = streaming.chunk();
                                String resultPreview = (result != null && result.length() > 200)
                                        ? result.substring(0, 200) + "..."
                                        : (result != null ? result : "");
                                sink.tryEmitNext("{\"type\":\"tool_result\",\"tool\":\""
                                        + escapeJson(toolName) + "\",\"result\":\""
                                        + escapeJson(resultPreview) + "\"}");
                            }
                        } else if (type == OutputType.AGENT_HOOK_STREAMING
                                || type == OutputType.AGENT_HOOK_FINISHED) {
                            String chunk = streaming.chunk();
                            if (chunk != null && !chunk.isEmpty()) {
                                sink.tryEmitNext("{\"type\":\"reasoning\",\"content\":\""
                                        + escapeJson(chunk) + "\"}");
                            }
                        }
                    }
                })
                .doOnComplete(() -> {
                    sink.tryEmitNext("[DONE]");
                    sink.tryEmitComplete();
                })
                .doOnError(error -> {
                    sink.tryEmitNext("{\"type\":\"error\",\"content\":\""
                            + escapeJson(error.getMessage()) + "\"}");
                    sink.tryEmitComplete();
                })
                .subscribe();

        return sink.asFlux();
    }

    @PostMapping("/messages")
    public ApiResponse<Map<String, Object>> callChat(@RequestBody ChatRequest request) {
        String message = request.getMessage();
        // 风险等级优先从 DB（User.riskLevel）读取，DB 读不到时回退到请求中的 riskLevel
        Long userId = currentUserId();
        String riskLevel = resolveRiskLevel(userId, request.getRiskLevel());

        // 拼接附件文本内容到消息中
        String enhancedMessage = message;
        List<ChatRequest.FileInfo> files = request.getFiles();
        if (files != null && !files.isEmpty()) {
            StringBuilder sb = new StringBuilder(message);
            for (ChatRequest.FileInfo file : files) {
                if (file.getTextContent() != null && !file.getTextContent().isBlank()) {
                    sb.append("\n\n[附件: ").append(file.getName()).append("]\n")
                            .append(file.getTextContent());
                }
            }
            enhancedMessage = sb.toString();
        }

        String result;
        if (riskLevel != null && !riskLevel.isBlank()) {
            result = (userId != null)
                    ? advisorAgent.call(enhancedMessage, userId, riskLevel).getText()
                    : advisorAgent.call(enhancedMessage, riskLevel).getText();
        } else {
            result = advisorAgent.call(enhancedMessage).getText();
        }
        return ApiResponse.success(Map.of("content", result, "role", "assistant"));
    }

    /**
     * 从 SecurityContext 提取当前登录用户ID（JwtAuthFilter 将 Long userId 作为 principal 注入）。
     * 未认证或异常时返回 null（实际 SecurityConfig 已强制认证，此处仅防御性处理）。
     */
    private Long currentUserId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Long id) {
                return id;
            }
            if (principal instanceof Number n) {
                return n.longValue();
            }
            if (principal instanceof String s && !s.isBlank() && !"anonymous".equals(s)) {
                return Long.valueOf(s);
            }
        } catch (Exception ignored) {
            // 防御性处理
        }
        return null;
    }

    /**
     * 解析风险等级：优先用 DB 中 User.riskLevel，DB 读不到时回退到请求中的 riskLevel。
     */
    private String resolveRiskLevel(Long userId, String requestRiskLevel) {
        if (userId != null) {
            try {
                User user = userService.findById(userId);
                String dbRiskLevel = user.getRiskLevel();
                if (dbRiskLevel != null && !dbRiskLevel.isBlank()) {
                    return dbRiskLevel;
                }
            } catch (Exception ignored) {
                // DB 读不到（用户不存在），回退到 request 的 riskLevel
            }
        }
        return requestRiskLevel;
    }

    private String extractToolName(String nodeName) {
        if (nodeName == null) return "unknown";
        if (nodeName.startsWith("agent_tool_")) {
            return nodeName.substring("agent_tool_".length());
        }
        return nodeName;
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
