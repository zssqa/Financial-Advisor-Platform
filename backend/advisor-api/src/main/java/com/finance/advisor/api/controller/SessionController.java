package com.finance.advisor.api.controller;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.common.session.SessionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话管理接口
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionManager sessionManager;

    public SessionController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 从 SecurityContext 提取当前登录用户ID（JwtAuthFilter 将 Long userId 作为 principal 注入）。
     * 未认证或异常时按 "anonymous" 兜底。
     */
    private String currentUserId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Long id) {
                return String.valueOf(id);
            }
            if (principal instanceof Number n) {
                return String.valueOf(n.longValue());
            }
            if (principal instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Exception ignored) {
            // 防御性处理
        }
        return "anonymous";
    }

    /**
     * 创建新会话（按当前登录用户隔离）
     */
    @PostMapping
    public ApiResponse<Map<String, String>> createSession() {
        String userId = currentUserId();
        String sessionId = sessionManager.createSession(userId);
        return ApiResponse.success(Map.of(
                "sessionId", sessionId,
                "threadId", sessionManager.getThreadId(userId, sessionId)));
    }

    /**
     * 获取会话信息（按当前登录用户隔离）
     */
    @GetMapping("/{sessionId}")
    public ApiResponse<SessionManager.SessionInfo> getSession(
            @PathVariable String sessionId) {
        SessionManager.SessionInfo info = sessionManager.getSessionInfo(currentUserId(), sessionId);
        if (info == null) {
            return ApiResponse.error(404, "会话不存在");
        }
        return ApiResponse.success(info);
    }

    /**
     * 删除会话（按当前登录用户隔离）
     */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<String> deleteSession(@PathVariable String sessionId) {
        sessionManager.removeSession(currentUserId(), sessionId);
        return ApiResponse.success("会话已删除");
    }

    /**
     * 列出当前登录用户的活跃会话
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listSessions() {
        List<Map<String, Object>> sessions = sessionManager.listActiveSessionInfos(currentUserId())
                .stream()
                .map(info -> Map.<String, Object>of(
                        "sessionId", info.getSessionId(),
                        "userId", info.getUserId(),
                        "createdAt", info.getCreatedAt(),
                        "lastActiveAt", info.getLastActiveAt()))
                .collect(Collectors.toList());
        return ApiResponse.success(sessions);
    }
}
