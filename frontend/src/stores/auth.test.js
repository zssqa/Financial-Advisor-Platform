import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { auth } from './auth.js'

describe('auth store', () => {
    beforeEach(() => {
        localStorage.clear()
        auth.token = null
        auth.user = null
    })

    it('setAuth: sets token, user, isLoggedIn and persists to localStorage', async () => {
        auth.setAuth('token123', { userId: 1, username: 'test', riskLevel: 'R3' })
        expect(auth.token).toBe('token123')
        expect(auth.isLoggedIn).toBe(true)
        expect(auth.user).toEqual({ userId: 1, username: 'test', riskLevel: 'R3' })

        // watch 持久化是异步的，需等待下一轮 tick
        await nextTick()
        const stored = JSON.parse(localStorage.getItem('fa_auth'))
        expect(stored.token).toBe('token123')
        expect(stored.user.userId).toBe(1)
        expect(stored.user.username).toBe('test')
    })

    it('clear: resets token and isLoggedIn', () => {
        auth.setAuth('token123', { userId: 1, username: 'test', riskLevel: 'R3' })
        expect(auth.isLoggedIn).toBe(true)

        auth.clear()
        expect(auth.token).toBe(null)
        expect(auth.isLoggedIn).toBe(false)
        expect(auth.user).toBe(null)
    })

    it('setRiskLevel: updates user.riskLevel', () => {
        auth.setAuth('token123', { userId: 1, username: 'test', riskLevel: 'R3' })
        auth.setRiskLevel('R5')
        expect(auth.user.riskLevel).toBe('R5')
    })
})
