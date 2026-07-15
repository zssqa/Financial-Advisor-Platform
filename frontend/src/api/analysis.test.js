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
import { getOptimize, getRiskReturn } from './analysis.js'

describe('analysis.js', () => {
    beforeEach(() => {
        http.get.mockReset()
        http.post.mockReset()
        http.put.mockReset()
        http.delete.mockReset()
    })

    it('getOptimize calls GET /portfolios/optimization and unwraps res.data.data', async () => {
        const payload = { weights: [{ asset: 'stock', weight: 0.6 }] }
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getOptimize()

        expect(http.get).toHaveBeenCalledWith('/portfolios/optimization')
        expect(result).toBe(payload)
    })

    it('getRiskReturn calls GET /portfolios/risk-return and unwraps res.data.data', async () => {
        const payload = [{ name: 'A', annualReturn: 0.1, annualVolatility: 0.2, marketValue: 1000 }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getRiskReturn()

        expect(http.get).toHaveBeenCalledWith('/portfolios/risk-return')
        expect(result).toBe(payload)
    })
})
