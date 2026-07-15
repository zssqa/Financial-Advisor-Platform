import { describe, it, expect, beforeEach } from 'vitest'
import { sessionsStore } from './sessions.js'

// happy-dom 可能不提供 crypto.randomUUID，按需 polyfill
if (!globalThis.crypto || !globalThis.crypto.randomUUID) {
    globalThis.crypto = {
        randomUUID: () => 'test-uuid-' + Math.random().toString(36).slice(2)
    }
}

describe('sessions store', () => {
    beforeEach(() => {
        localStorage.clear()
        sessionsStore.clearCurrentUserSessions()
    })

    it('user isolation: user 2 does not see user 1 sessions', () => {
        sessionsStore.loadUserSessions(1)
        sessionsStore.createSession()
        expect(sessionsStore.state.sessions.length).toBe(1)

        // 切换到用户 2，应看不到用户 1 的会话
        sessionsStore.loadUserSessions(2)
        expect(sessionsStore.state.sessions.length).toBe(0)
        expect(sessionsStore.state.currentId).toBe(null)
    })

    it('createSession: creates session with UUID, title "新会话", empty messages', () => {
        sessionsStore.loadUserSessions(1)
        const session = sessionsStore.createSession()
        expect(session.id).toBeTruthy()
        expect(typeof session.id).toBe('string')
        expect(session.title).toBe('新会话')
        expect(session.messages).toEqual([])
        expect(session.draft).toBe('')
        // 新创建的会话应自动选中
        expect(sessionsStore.state.currentId).toBe(session.id)
    })

    it('selectSession: selects the specified session', () => {
        sessionsStore.loadUserSessions(1)
        const s1 = sessionsStore.createSession()
        const s2 = sessionsStore.createSession()
        // createSession 自动选中最新，先切换到第一个再选第二个
        sessionsStore.selectSession(s1.id)
        expect(sessionsStore.state.currentId).toBe(s1.id)

        sessionsStore.selectSession(s2.id)
        expect(sessionsStore.state.currentId).toBe(s2.id)
        expect(sessionsStore.current.value.id).toBe(s2.id)
    })

    it('deleteSession: removes session and auto-selects another', () => {
        sessionsStore.loadUserSessions(1)
        const s1 = sessionsStore.createSession()
        const s2 = sessionsStore.createSession()
        // 当前选中 s2（最后创建的）
        expect(sessionsStore.state.currentId).toBe(s2.id)

        sessionsStore.deleteSession(s2.id)
        expect(sessionsStore.state.sessions.length).toBe(1)
        // 删除当前会话后应自动选中另一个
        expect(sessionsStore.state.currentId).toBe(s1.id)
    })

    it('renameByFirstMessage: truncates title to 20 chars + ellipsis', () => {
        sessionsStore.loadUserSessions(1)
        const session = sessionsStore.createSession()
        const longMessage = '这是一个非常非常非常非常非常非常非常非常非常长的消息内容'
        sessionsStore.renameByFirstMessage(session.id, longMessage)
        const updated = sessionsStore.state.sessions.find(s => s.id === session.id)
        expect(updated.title.length).toBe(23) // 20 chars + '...'
        expect(updated.title).toBe(longMessage.substring(0, 20) + '...')
    })

    it('renameByFirstMessage: keeps short title as-is', () => {
        sessionsStore.loadUserSessions(1)
        const session = sessionsStore.createSession()
        const shortMessage = '你好世界'
        sessionsStore.renameByFirstMessage(session.id, shortMessage)
        const updated = sessionsStore.state.sessions.find(s => s.id === session.id)
        expect(updated.title).toBe('你好世界')
    })

    it('saveDraft: stores draft text on session', () => {
        sessionsStore.loadUserSessions(1)
        const session = sessionsStore.createSession()
        sessionsStore.saveDraft(session.id, 'hello')
        const updated = sessionsStore.state.sessions.find(s => s.id === session.id)
        expect(updated.draft).toBe('hello')
    })
})
