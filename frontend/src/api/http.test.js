import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import http from './http.js'
import { auth } from '../stores/auth.js'

describe('http interceptors', () => {
    const originalLocation = window.location

    afterEach(() => {
        // 恢复 window.location
        Object.defineProperty(window, 'location', {
            value: originalLocation,
            writable: true,
            configurable: true
        })
    })

    beforeEach(() => {
        localStorage.clear()
        auth.token = null
        auth.user = null
    })

    it('request interceptor attaches Bearer token when auth.token is set', async () => {
        auth.token = 'abc123'
        const config = { headers: {} }

        // 直接调用请求拦截器的 fulfilled 处理函数
        const handler = http.interceptors.request.handlers[0]
        const result = await handler.fulfilled(config)

        expect(result.headers.Authorization).toBe('Bearer abc123')
    })

    it('request interceptor does not attach Authorization when no token', async () => {
        auth.token = null
        const config = { headers: {} }

        const handler = http.interceptors.request.handlers[0]
        const result = await handler.fulfilled(config)

        expect(result.headers.Authorization).toBeUndefined()
    })

    it('401 response interceptor clears auth and redirects to /login', async () => {
        auth.token = 'oldtoken'
        auth.user = { userId: 1, username: 'test' }

        // mock window.location（pathname 非 /login 以触发跳转）
        Object.defineProperty(window, 'location', {
            value: { href: '', pathname: '/dashboard' },
            writable: true,
            configurable: true
        })

        const handler = http.interceptors.response.handlers[0]
        // rejected 返回 Promise.reject，需要 catch
        await handler.rejected({ response: { status: 401 } }).catch(() => {})

        expect(auth.token).toBe(null)
        expect(auth.isLoggedIn).toBe(false)
        expect(window.location.href).toBe('/login')
    })

    it('non-401 error does not clear auth or redirect', async () => {
        auth.token = 'keepme'

        Object.defineProperty(window, 'location', {
            value: { href: '', pathname: '/dashboard' },
            writable: true,
            configurable: true
        })

        const handler = http.interceptors.response.handlers[0]
        await handler.rejected({ response: { status: 500 } }).catch(() => {})

        expect(auth.token).toBe('keepme')
        expect(window.location.href).toBe('')
    })
})
