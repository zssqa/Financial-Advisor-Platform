import http from './http.js'

export function createSession() {
    return http.post('/session/create').then(res => res.data)
}

export function getSession(sessionId) {
    return http.get(`/session/${encodeURIComponent(sessionId)}`).then(res => res.data)
}

export function listSessions() {
    return http.get('/session/list').then(res => res.data)
}
