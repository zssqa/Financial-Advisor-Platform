import http from './http.js'

export function listAssets() {
    return http.get('/portfolios').then(res => res.data.data)
}

export function createAsset(asset) {
    return http.post('/portfolios', asset).then(res => res.data.data)
}

export function updateAsset(id, asset) {
    return http.put(`/portfolios/${id}`, asset).then(res => res.data.data)
}

export function deleteAsset(id) {
    return http.delete(`/portfolios/${id}`).then(res => res.data.data)
}

export function getSummary() {
    return http.get('/portfolios/summary').then(res => res.data.data)
}

// 获取最近 N 天的市值历史（盈亏趋势）
export function getPortfolioHistory(days = 30) {
    return http.get('/portfolios/history', { params: { days } }).then(res => res.data.data)
}

export function importAssets(file) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post('/portfolios:import', formData, { timeout: 120000 }).then(res => res.data.data)
}

export function downloadTemplate() {
    return http.get('/portfolios/template', { responseType: 'blob' }).then(res => res)
}
