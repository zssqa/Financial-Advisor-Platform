package com.finance.advisor.user;

/**
 * 用户信息响应
 */
public class UserProfile {

    private Long userId;
    private String username;
    private String riskLevel;
    private Long createdAt;

    public UserProfile() {
    }

    public UserProfile(Long userId, String username, String riskLevel, Long createdAt) {
        this.userId = userId;
        this.username = username;
        this.riskLevel = riskLevel;
        this.createdAt = createdAt;
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

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
