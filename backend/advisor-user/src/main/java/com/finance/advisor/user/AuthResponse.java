package com.finance.advisor.user;

/**
 * 认证响应（登录/注册成功返回）
 */
public class AuthResponse {

    private String token;
    private Long userId;
    private String username;
    private String riskLevel;

    public AuthResponse() {
    }

    public AuthResponse(String token, Long userId, String username, String riskLevel) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.riskLevel = riskLevel;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
}
