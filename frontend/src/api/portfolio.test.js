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
    listAssets,
    createAsset,
    updateAsset,
    deleteAsset,
    getSummary,
    getPortfolioHistory,
    importAssets,
    downloadTemplate
} from './portfolio.js'

describe('portfolio.js', () => {
    beforeEach(() => {
        http.get.mockReset()
        http.post.mockReset()
        http.put.mockReset()
        http.delete.mockReset()
    })

    it('listAssets calls GET /portfolios and unwraps res.data.data', async () => {
        const payload = [{ id: 1, type: 'stock' }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await listAssets()

        expect(http.get).toHaveBeenCalledWith('/portfolios')
        expect(result).toBe(payload)
    })

    it('createAsset calls POST /portfolios with body=asset', async () => {
        const payload = { id: 2, type: 'fund' }
        http.post.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })
        const asset = { type: 'fund', code: '001', amount: 100 }

        const result = await createAsset(asset)

        expect(http.post).toHaveBeenCalledWith('/portfolios', asset)
        expect(result).toBe(payload)
    })

    it('updateAsset calls PUT /portfolios/${id} with body=asset', async () => {
        const payload = { id: 5, type: 'stock' }
        http.put.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })
        const asset = { type: 'stock', amount: 20 }

        const result = await updateAsset(5, asset)

        expect(http.put).toHaveBeenCalledWith('/portfolios/5', asset)
        expect(result).toBe(payload)
    })

    it('deleteAsset calls DELETE /portfolios/${id}', async () => {
        const payload = { success: true }
        http.delete.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await deleteAsset(7)

        expect(http.delete).toHaveBeenCalledWith('/portfolios/7')
        expect(result).toBe(payload)
    })

    it('getSummary calls GET /portfolios/summary and unwraps res.data.data', async () => {
        const payload = { totalValue: 10000, profit: 200 }
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getSummary()

        expect(http.get).toHaveBeenCalledWith('/portfolios/summary')
        expect(result).toBe(payload)
    })

    it('getPortfolioHistory calls GET /portfolios/history with params {days}', async () => {
        const payload = [{ date: '2026-07-01', value: 100 }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getPortfolioHistory()

        expect(http.get).toHaveBeenCalledWith('/portfolios/history', { params: { days: 30 } })
        expect(result).toBe(payload)
    })

    it('importAssets calls POST /portfolios:import with FormData and timeout:120000', async () => {
        const payload = { imported: 3 }
        http.post.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const file = new File(['a,b,c\n1,2,3'], 'assets.csv', { type: 'text/csv' })
        const result = await importAssets(file)

        const call = http.post.mock.calls[0]
        expect(call[0]).toBe('/portfolios:import')
        expect(call[1]).toBeInstanceOf(FormData)
        expect(call[2]).toEqual({ timeout: 120000 })
        expect(result).toBe(payload)
    })

    it('downloadTemplate calls GET /portfolios/template with blob responseType and returns full res', async () => {
        const fullRes = {
            data: 'blob-bytes',
            headers: { 'content-type': 'application/octet-stream' },
            status: 200
        }
        http.get.mockResolvedValue(fullRes)

        const result = await downloadTemplate()

        expect(http.get).toHaveBeenCalledWith('/portfolios/template', { responseType: 'blob' })
        // downloadTemplate returns res directly (not res.data.data)
        expect(result).toBe(fullRes)
    })
})
