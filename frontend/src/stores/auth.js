import { reactive, watch } from 'vue'
import { sessionsStore } from './sessions.js'

const STORAGE_KEY = 'fa_auth'

function loadFromStorage() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY)
        if (!raw) return { token: null, user: null }
        const parsed = JSON.parse(raw)
        return {
            token: parsed.token ?? null,
            user: parsed.user ?? null
        }
    } catch {
        return { token: null, user: null }
    }
}

const initial = loadFromStorage()

export const auth = reactive({
    token: initial.token,
    user: initial.user,
    get isLoggedIn() {
        return !!this.token
    },
    setAuth(token, user) {
        this.token = token
        this.user = user
        // 切换到新用户的会话（按 userId 隔离加载）
        if (user?.userId != null) {
            sessionsStore.loadUserSessions(user.userId)
        }
    },
    clear() {
        // 登出时清除当前用户会话引用（保留存储以便重新登录恢复）
        sessionsStore.clearCurrentUserSessions()
        this.token = null
        this.user = null
    },
    setRiskLevel(level) {
        if (this.user) {
            this.user.riskLevel = level
        }
    }
})

watch(
    auth,
    (val) => {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: val.token, user: val.user }))
        } catch { /* ignore */ }
    },
    { deep: true }
)
