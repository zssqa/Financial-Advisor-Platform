import http from './http.js'

/**
 * 注册：返回 {token, userId, username, riskLevel}
 */
export function register(username, password) {
    return http.post('/auth/register', { username, password })
        .then(res => res.data.data)
}

/**
 * 登录：返回 {token, userId, username, riskLevel}
 */
export function login(username, password) {
    return http.post('/auth/login', { username, password })
        .then(res => res.data.data)
}

/**
 * 获取当前用户资料
 */
export function getProfile() {
    return http.get('/auth/profile')
        .then(res => res.data.data)
}

/**
 * 更新风险等级
 */
export function updateRiskLevel(riskLevel) {
    return http.put('/auth/risk-level', { riskLevel })
        .then(res => res.data.data)
}
