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
import { register, login, getProfile, updateRiskLevel } from './auth.js'

describe('auth.js', () => {
    beforeEach(() => {
        http.get.mockReset()
        http.post.mockReset()
        http.put.mockReset()
        http.delete.mockReset()
    })

    it('register calls POST /auth/register with {username, password} and unwraps res.data.data', async () => {
        const payload = { token: 't1', userId: 1, username: 'alice', riskLevel: 3 }
        http.post.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await register('alice', 'pw123')

        expect(http.post).toHaveBeenCalledWith('/auth/register', { username: 'alice', password: 'pw123' })
        expect(result).toBe(payload)
    })

    it('login calls POST /auth/login with {username, password} and unwraps res.data.data', async () => {
        const payload = { token: 't2', userId: 2, username: 'bob', riskLevel: 2 }
        http.post.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await login('bob', 'secret')

        expect(http.post).toHaveBeenCalledWith('/auth/login', { username: 'bob', password: 'secret' })
        expect(result).toBe(payload)
    })

    it('getProfile calls GET /auth/profile and unwraps res.data.data', async () => {
        const payload = { userId: 2, username: 'bob', riskLevel: 2 }
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getProfile()

        expect(http.get).toHaveBeenCalledWith('/auth/profile')
        expect(result).toBe(payload)
    })

    it('updateRiskLevel calls PUT /auth/risk-level with {riskLevel} and unwraps res.data.data', async () => {
        const payload = { userId: 2, username: 'bob', riskLevel: 5 }
        http.put.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await updateRiskLevel(5)

        expect(http.put).toHaveBeenCalledWith('/auth/risk-level', { riskLevel: 5 })
        expect(result).toBe(payload)
    })
})
