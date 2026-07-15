import http from './http.js'

export function listGoals() {
    return http.get('/goals').then(res => res.data.data)
}

export function createGoal(goal) {
    return http.post('/goals', goal).then(res => res.data.data)
}

export function updateGoal(id, goal) {
    return http.put(`/goals/${id}`, goal).then(res => res.data.data)
}

export function deleteGoal(id) {
    return http.delete(`/goals/${id}`).then(res => res.data.data)
}

export function getSummary() {
    return http.get('/goals/summary').then(res => res.data.data)
}
