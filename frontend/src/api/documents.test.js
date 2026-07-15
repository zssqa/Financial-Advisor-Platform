import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock the http module BEFORE importing the API file
vi.mock('./http.js', () => {
    const http = {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
    return { default: http }
})

import http from './http.js'
import {
    listDocuments,
    uploadDocument,
    searchDocuments,
    queryByCategory,
    getStats,
    ingestFromWeb
} from './documents.js'

describe('documents.js', () => {
    beforeEach(() => {
        http.get.mockReset()
        http.post.mockReset()
        http.put.mockReset()
        http.delete.mockReset()
    })

    it('listDocuments calls GET /documents with no params and unwraps res.data.data', async () => {
        const payload = [{ id: 1, title: 'doc1' }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await listDocuments()

        expect(http.get).toHaveBeenCalledWith('/documents')
        expect(result).toBe(payload)
    })

    it('uploadDocument calls POST /documents with FormData and timeout:120000', async () => {
        const payload = { id: 9, title: 'uploaded' }
        http.post.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const file = new File(['content'], 'note.txt', { type: 'text/plain' })
        const result = await uploadDocument(file)

        const call = http.post.mock.calls[0]
        expect(call[0]).toBe('/documents')
        expect(call[1]).toBeInstanceOf(FormData)
        expect(call[2]).toEqual({ timeout: 120000 })
        expect(result).toBe(payload)
    })

    it('searchDocuments calls GET /documents with params {keyword: query}', async () => {
        const payload = [{ id: 2, title: 'match' }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await searchDocuments('stock')

        expect(http.get).toHaveBeenCalledWith('/documents', { params: { keyword: 'stock' } })
        expect(result).toBe(payload)
    })

    it('queryByCategory calls GET /documents/category/<encoded category>', async () => {
        const payload = [{ id: 3, title: 'cat-doc' }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await queryByCategory('tech fund')

        // encodeURIComponent('tech fund') === 'tech%20fund'
        expect(http.get).toHaveBeenCalledWith('/documents/category/tech%20fund')
        expect(result).toBe(payload)
    })

    it('getStats calls GET /documents/statistics and unwraps res.data.data', async () => {
        const payload = { total: 10, categories: 3 }
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getStats()

        expect(http.get).toHaveBeenCalledWith('/documents/statistics')
        expect(result).toBe(payload)
    })

    it('ingestFromWeb calls POST /documents:ingest-web with {keyword}', async () => {
        const payload = { ingested: 5 }
        http.post.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await ingestFromWeb('AI')

        expect(http.post).toHaveBeenCalledWith('/documents:ingest-web', { keyword: 'AI' })
        expect(result).toBe(payload)
    })
})
