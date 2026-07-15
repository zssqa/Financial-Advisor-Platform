import http from './http.js'

export function listDocuments() {
    return http.get('/documents').then(res => res.data.data)
}

export function uploadDocument(file) {
    const formData = new FormData()
    formData.append('file', file)
    // 不手动设置 Content-Type，让浏览器自动生成带 boundary 的 multipart header
    return http.post('/documents', formData, {
        timeout: 120000
    }).then(res => res.data.data)
}

export function searchDocuments(query) {
    return http.get('/documents', { params: { keyword: query } }).then(res => res.data.data)
}

export function queryByCategory(category) {
    return http.get(`/documents/category/${encodeURIComponent(category)}`).then(res => res.data.data)
}

export function getStats() {
    return http.get('/documents/statistics').then(res => res.data.data)
}

export function ingestFromWeb(keyword) {
    return http.post('/documents:ingest-web', { keyword }).then(res => res.data.data)
}
