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
import { screenFunds, exchangeRate } from './tool.js'

describe('tool.js', () => {
    beforeEach(() => {
        http.get.mockReset()
        http.post.mockReset()
        http.put.mockReset()
        http.delete.mockReset()
    })

    it('screenFunds calls POST /funds:screen with body=data and unwraps res.data.data', async () => {
        const payload = [{ code: '001', name: 'Fund A' }]
        http.post.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })
        const data = { type: 'stock', minScore: 80 }

        const result = await screenFunds(data)

        expect(http.post).toHaveBeenCalledWith('/funds:screen', data)
        expect(result).toBe(payload)
    })

    it('exchangeRate calls GET /exchange-rates with params {from, to, amount}', async () => {
        const payload = { from: 'USD', to: 'CNY', amount: 100, result: 720 }
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await exchangeRate('USD', 'CNY', 100)

        expect(http.get).toHaveBeenCalledWith('/exchange-rates', { params: { from: 'USD', to: 'CNY', amount: 100 } })
        expect(result).toBe(payload)
    })
})
