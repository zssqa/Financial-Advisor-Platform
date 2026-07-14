import http from './http.js'

export function listAssets() {
    return http.get('/portfolio/list').then(res => res.data.data)
}

export function createAsset(asset) {
    return http.post('/portfolio', asset).then(res => res.data.data)
}

export function updateAsset(id, asset) {
    return http.put(`/portfolio/${id}`, asset).then(res => res.data.data)
}

export function deleteAsset(id) {
    return http.delete(`/portfolio/${id}`).then(res => res.data.data)
}

export function getSummary() {
    return http.get('/portfolio/summary').then(res => res.data.data)
}

export function importAssets(file) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post('/portfolio/import', formData, { timeout: 120000 }).then(res => res.data.data)
}

export function downloadTemplate() {
    return http.get('/portfolio/template', { responseType: 'blob' }).then(res => res)
}
