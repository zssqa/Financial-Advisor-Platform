import http from './http.js'
import { auth } from '../stores/auth.js'

const DEBUG_SSE = import.meta.env?.VITE_DEBUG_SSE === 'true'

/**
 * SSE 流式对话（支持 JSON 结构化事件）
 * 事件类型:
 *   - { type: "reasoning", content: "..." } - 推理过程
 *   - { type: "message", content: "..." }   - 回复内容
 *   - { type: "tool_call", tool: "...", args: "..." } - 工具调用开始
 *   - { type: "tool_result", tool: "...", result: "..." } - 工具调用完成
 *   - { type: "error", content: "..." } - 错误
 *   - [DONE] - 完成
 */
export function streamChat(message, onMessage, onReasoning, onToolCall, onToolResult, onDone, onError, files, riskLevel) {
    const controller = new AbortController()
    let doneCalled = false
    function triggerDone() {
        if (doneCalled) return
        doneCalled = true
        onDone && onDone()
    }

    const body = { message }
    if (files && files.length > 0) {
        body.files = files.map(f => ({ name: f.name, type: f.type, textContent: f.textContent }))
    }
    if (riskLevel) {
        body.riskLevel = riskLevel
    }

    const headers = { 'Content-Type': 'application/json' }
    if (auth.token) {
        headers.Authorization = 'Bearer ' + auth.token
    }

    fetch('/api/chat/stream', {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: controller.signal
    }).then(async (response) => {
        if (!response.ok) {
            onError && onError('stream request failed: ' + response.status + ' ' + response.statusText)
            return
        }
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
            const { done, value } = await reader.read()
            if (done) {
                // 处理剩余 buffer 中的最后一个事件
                if (buffer.trim().startsWith('data:')) {
                    const data = buffer.trim().slice(5).trim()
                    if (data === '[DONE]') {
                        triggerDone()
                    } else if (data) {
                        try {
                            const parsed = JSON.parse(data)
                            if (parsed.type === 'message') onMessage && onMessage(parsed.content)
                            else if (parsed.type === 'error') onError && onError(parsed.content)
                        } catch { /* ignore */ }
                    }
                }
                triggerDone()
                return
            }

            buffer += decoder.decode(value, { stream: true })
            // 按 'data:' 分割事件（而非 \n），避免内容中的换行符导致 JSON 解析失败
            const parts = buffer.split('data:')
            buffer = parts.pop() || ''

            for (const part of parts) {
                const data = part.trim()
                if (!data) continue

                if (DEBUG_SSE) console.log('SSE part:', data.substring(0, 80))

                if (data === '[DONE]') {
                    triggerDone()
                    return
                }

                try {
                    const parsed = JSON.parse(data)
                    if (parsed.type === 'reasoning') {
                        onReasoning && onReasoning(parsed.content)
                    } else if (parsed.type === 'message') {
                        onMessage && onMessage(parsed.content)
                    } else if (parsed.type === 'tool_call') {
                        onToolCall && onToolCall(parsed.tool, parsed.args)
                    } else if (parsed.type === 'tool_result') {
                        onToolResult && onToolResult(parsed.tool, parsed.result)
                    } else if (parsed.type === 'error') {
                        onError && onError(parsed.content)
                        return
                    }
                } catch {
                    // 解析失败时跳过，不中断流
                }
            }
        }
    }).catch(err => {
        if (err.name !== 'AbortError') {
            onError && onError(err.message)
        }
    })

    return controller
}

/**
 * 非流式对话
 */
export function callChat(message, riskLevel) {
    const body = { message }
    if (riskLevel) body.riskLevel = riskLevel
    return http.post('/chat/call', body)
}
