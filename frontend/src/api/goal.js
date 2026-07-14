import http from './http.js'

export function listGoals() {
    return http.get('/goal/list').then(res => res.data.data)
}

export function createGoal(goal) {
    return http.post('/goal', goal).then(res => res.data.data)
}

export function updateGoal(id, goal) {
    return http.put(`/goal/${id}`, goal).then(res => res.data.data)
}

export function deleteGoal(id) {
    return http.delete(`/goal/${id}`).then(res => res.data.data)
}

export function getSummary() {
    return http.get('/goal/summary').then(res => res.data.data)
}
