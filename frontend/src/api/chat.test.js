import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { streamChat } from './chat.js'

// 辅助：根据 SSE 文本块数组构造一个 fetch Response
function makeResponse(chunks, { ok = true, status = 200 } = {}) {
    const encoder = new TextEncoder()
    const stream = new ReadableStream({
        start(controller) {
            for (const chunk of chunks) {
                controller.enqueue(encoder.encode(chunk))
            }
            controller.close()
        }
    })
    return new Response(stream, { status, statusText: ok ? 'OK' : 'Error' })
}

// 等待所有微任务 + 宏任务刷新
function flush(ms = 50) {
    return new Promise(resolve => setTimeout(resolve, ms))
}

describe('streamChat SSE parsing', () => {
    let originalFetch

    beforeEach(() => {
        originalFetch = global.fetch
    })

    afterEach(() => {
        global.fetch = originalFetch
        vi.restoreAllMocks()
    })

    it('SSE message event: calls onMessage with content', async () => {
        const onMessage = vi.fn()
        const onDone = vi.fn()
        // 末尾追加 [DONE] 事件以触发 buffer 中消息事件的解析（split('data:') 机制）
        global.fetch = vi.fn().mockResolvedValue(
            makeResponse(['data: {"type":"message","content":"hello"}\n\ndata: [DONE]\n\n'])
        )

        streamChat('hi', onMessage, undefined, undefined, undefined, onDone, undefined)
        await flush()

        expect(onMessage).toHaveBeenCalledWith('hello')
        expect(onDone).toHaveBeenCalled()
    })

    it('SSE [DONE] terminator: calls onDone', async () => {
        const onDone = vi.fn()
        global.fetch = vi.fn().mockResolvedValue(
            makeResponse(['data: [DONE]\n\n'])
        )

        streamChat('hi', undefined, undefined, undefined, undefined, onDone, undefined)
        await flush()

        expect(onDone).toHaveBeenCalled()
    })

    it('SSE error event: calls onError with content', async () => {
        const onError = vi.fn()
        // 末尾追加 [DONE] 事件以触发 buffer 中错误事件的解析
        global.fetch = vi.fn().mockResolvedValue(
            makeResponse(['data: {"type":"error","content":"oops"}\n\ndata: [DONE]\n\n'])
        )

        streamChat('hi', undefined, undefined, undefined, undefined, undefined, onError)
        await flush()

        expect(onError).toHaveBeenCalledWith('oops')
    })

    it('AbortController: aborting does not trigger onError', async () => {
        const onError = vi.fn()
        // 模拟 fetch 在 abort 时以 AbortError 拒绝
        global.fetch = vi.fn().mockImplementation((url, opts) => {
            return new Promise((resolve, reject) => {
                if (opts.signal) {
                    opts.signal.addEventListener('abort', () => {
                        const err = new Error('The operation was aborted')
                        err.name = 'AbortError'
                        reject(err)
                    })
                }
            })
        })

        const controller = streamChat('hi', undefined, undefined, undefined, undefined, undefined, onError)
        controller.abort()
        await flush()

        // AbortError 不应触发 onError
        expect(onError).not.toHaveBeenCalled()
    })
})
