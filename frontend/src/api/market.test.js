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
import { getMarketIndices, getMarketSentiment, getMarketCalendar } from './market.js'

describe('market.js', () => {
    beforeEach(() => {
        http.get.mockReset()
        http.post.mockReset()
        http.put.mockReset()
        http.delete.mockReset()
    })

    it('getMarketIndices calls GET /markets/indices and unwraps res.data.data', async () => {
        const payload = [{ name: '上证指数', value: 3000 }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getMarketIndices()

        expect(http.get).toHaveBeenCalledWith('/markets/indices')
        expect(result).toBe(payload)
    })

    it('getMarketSentiment calls GET /markets/sentiment and unwraps res.data.data', async () => {
        const payload = { value: 75, label: 'Greed' }
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getMarketSentiment()

        expect(http.get).toHaveBeenCalledWith('/markets/sentiment')
        expect(result).toBe(payload)
    })

    it('getMarketCalendar calls GET /markets/calendar and unwraps res.data.data', async () => {
        const payload = [{ date: '2026-07-15', event: 'CPI' }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getMarketCalendar()

        expect(http.get).toHaveBeenCalledWith('/markets/calendar')
        expect(result).toBe(payload)
    })
})
