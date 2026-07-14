import { reactive, computed, watch } from 'vue'

const CURRENT_KEY = 'fa_current_session'

// 按 userId 隔离的存储 key，避免不同用户共享会话
function storageKey(userId) {
    return `fa_sessions_${userId || 'default'}`
}

function loadFromStorage(userId) {
    try {
        const raw = localStorage.getItem(storageKey(userId))
        return raw ? JSON.parse(raw) : []
    } catch {
        return []
    }
}

// 从 localStorage 的 fa_auth 读取当前登录用户 userId（避免与 auth.js 循环依赖）
function getCurrentUserId() {
    try {
        const raw = localStorage.getItem('fa_auth')
        if (!raw) return null
        const parsed = JSON.parse(raw)
        return parsed?.user?.userId ?? null
    } catch {
        return null
    }
}

const state = reactive({
    sessions: loadFromStorage(getCurrentUserId()),
    currentId: localStorage.getItem(CURRENT_KEY) || null,
    currentUserId: getCurrentUserId()
})

function persist() {
    if (state.currentUserId) {
        localStorage.setItem(storageKey(state.currentUserId), JSON.stringify(state.sessions))
    }
    if (state.currentId) {
        localStorage.setItem(CURRENT_KEY, state.currentId)
    } else {
        localStorage.removeItem(CURRENT_KEY)
    }
}

watch(() => state.sessions, persist, { deep: true })
watch(() => state.currentId, persist)

export const sessionsStore = {
    state,
    list: computed(() => state.sessions),
    current: computed(() => state.sessions.find(s => s.id === state.currentId) || null),

    // 切换到指定用户的会话（登录时调用）
    loadUserSessions(userId) {
        state.currentUserId = userId
        state.sessions = loadFromStorage(userId)
        // 若加载的会话列表非空，选中第一个；否则置空
        state.currentId = state.sessions.length > 0 ? state.sessions[0].id : null
    },

    // 清除当前用户会话引用（登出时调用，不删除存储）
    clearCurrentUserSessions() {
        state.sessions = []
        state.currentId = null
        state.currentUserId = null
    },

    createSession() {
        const session = {
            id: crypto.randomUUID(),
            title: '新会话',
            messages: [],
            draft: '',
            createdAt: Date.now(),
            updatedAt: Date.now()
        }
        state.sessions.unshift(session)
        state.currentId = session.id
        return session
    },

    selectSession(id) {
        state.currentId = id
    },

    deleteSession(id) {
        const idx = state.sessions.findIndex(s => s.id === id)
        if (idx >= 0) {
            state.sessions.splice(idx, 1)
            if (state.currentId === id) {
                state.currentId = state.sessions[0]?.id || null
            }
        }
    },

    clearAll() {
        state.sessions.splice(0, state.sessions.length)
        state.currentId = null
        if (state.currentUserId) {
            localStorage.removeItem(storageKey(state.currentUserId))
        }
        localStorage.removeItem(CURRENT_KEY)
    },

    renameByFirstMessage(sessionId, message) {
        const session = state.sessions.find(s => s.id === sessionId)
        if (session && (session.title === '新会话' || !session.title)) {
            session.title = message.length > 20 ? message.substring(0, 20) + '...' : message
            session.updatedAt = Date.now()
        }
    },

    addMessage(sessionId, message) {
        const session = state.sessions.find(s => s.id === sessionId)
        if (session) {
            if (!message.createdAt) message.createdAt = Date.now()
            session.messages.push(message)
            session.updatedAt = Date.now()
        }
    },

    updateMessage(sessionId, index, updater) {
        const session = state.sessions.find(s => s.id === sessionId)
        if (session && session.messages[index]) {
            updater(session.messages[index])
            session.updatedAt = Date.now()
        }
    },

    saveDraft(sessionId, draft) {
        const session = state.sessions.find(s => s.id === sessionId)
        if (session) {
            session.draft = draft
        }
    },

    touch(sessionId) {
        const session = state.sessions.find(s => s.id === sessionId)
        if (session) {
            session.updatedAt = Date.now()
        }
    }
}
