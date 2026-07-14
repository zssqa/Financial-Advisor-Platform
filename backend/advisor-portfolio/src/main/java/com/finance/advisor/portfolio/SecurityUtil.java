package com.finance.advisor.portfolio;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 提取当前登录用户 ID 的工具。
 *
 * JwtAuthFilter 在登录后将 userId 作为 Authentication 的 principal 放入 SecurityContext。
 * 本工具兼容 principal 为 Long / Number / String 的形态，统一转换为 Long 返回。
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前用户 ID
     * @throws IllegalStateException 当请求未携带认证用户或用户 ID 无法解析时
     */
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("当前请求未携带认证用户");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof Number) {
            return ((Number) principal).longValue();
        }
        String s = principal.toString();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("无法解析当前用户 ID: " + s);
        }
    }
}
