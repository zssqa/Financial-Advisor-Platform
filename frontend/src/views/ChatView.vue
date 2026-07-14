<template>
    <AppLayout>
        <template #sidebar>
            <SessionSidebar
                :sessions="state.sessions"
                :current-id="state.currentId"
                @new-session="handleNewSession"
                @select="handleSelect"
                @delete="handleDelete"
            />
        </template>

        <div class="chat-view">
            <div class="chat-main">
                <MessageList
                    ref="messageListRef"
                    :messages="currentMessages"
                    :suggestions="suggestions"
                    :streaming="loading"
                    @send="handleSend"
                    @confirm="handleConfirm"
                />
                <MessageInput
                    ref="inputRef"
                    :disabled="loading"
                    :draft="currentDraft"
                    @send="handleSend"
                    @update:draft="handleDraftUpdate"
                />
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useMessage } from 'naive-ui'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import MessageList from '../components/MessageList.vue'
import MessageInput from '../components/MessageInput.vue'
import { streamChat } from '../api/chat.js'
import { sessionsStore } from '../stores/sessions.js'
import { settings } from '../stores/settings.js'

const { state, current, createSession, selectSession, deleteSession,
    renameByFirstMessage, addMessage, updateMessage, saveDraft } = sessionsStore

const message = useMessage()
const messageListRef = ref(null)
const inputRef = ref(null)
const loading = ref(false)
const isUnmounted = ref(false)
let abortController = null

const currentMessages = computed(() => current.value?.messages || [])
const currentDraft = computed(() => current.value?.draft || '')

const suggestions = [
    '50万闲钱如何理财？',
    '基金定投和一次性投入哪个好？',
    '计算10万元年利率5%投资5年的复利收益',
    '当前A股市场行情如何？'
]

onMounted(() => {
    if (state.sessions.length === 0) {
        createSession()
    } else if (!state.currentId) {
        selectSession(state.sessions[0].id)
    }
    nextTick(() => inputRef.value?.focus())
})

function handleNewSession() {
    createSession()
    nextTick(() => inputRef.value?.focus())
}

function handleSelect(id) {
    selectSession(id)
    nextTick(() => inputRef.value?.focus())
}

function handleDelete(id) {
    deleteSession(id)
    if (!state.currentId && state.sessions.length > 0) {
        selectSession(state.sessions[0].id)
    }
}

function handleDraftUpdate(text) {
    if (state.currentId) saveDraft(state.currentId, text)
}

function handleSend(text) {
    const content = (text || '').trim()
    if (!content || loading.value) return

    const sessionId = state.currentId
    if (!sessionId) return

    // 若上一次请求仍在进行，先中止旧请求再发起新请求，避免并发残留
    if (abortController) {
        try { abortController.abort() } catch { /* ignore */ }
        abortController = null
    }

    if (!current.value || current.value.title === '新会话') {
        renameByFirstMessage(sessionId, content)
    }

    addMessage(sessionId, { role: 'user', content })

    const aiMsg = { role: 'assistant', content: '', streaming: true, showReasoning: settings.showReasoning, toolCalls: [], showConfirmation: false }
    addMessage(sessionId, aiMsg)

    const aiIndex = current.value.messages.length - 1
    loading.value = true

    const files = inputRef.value?.getFiles() || []
    const riskLevel = settings.riskLevel

    abortController = streamChat(
        content,
        (chunk) => updateMessage(sessionId, aiIndex, m => m.content += chunk),
        (reasoning) => updateMessage(sessionId, aiIndex, m => {
            m.reasoning = (m.reasoning || '') + reasoning
            m.showReasoning = true
        }),
        (tool, args) => updateMessage(sessionId, aiIndex, m => {
            m.toolCalls.push({ tool: tool || '未知工具', args, status: 'running', result: '' })
        }),
        (tool, result) => updateMessage(sessionId, aiIndex, m => {
            const tc = m.toolCalls.find(t => t.tool === tool && t.status === 'running')
            if (tc) { tc.status = 'done'; tc.result = result || '已完成' }
        }),
        () => {
            // 组件已卸载时静默处理，避免对已销毁的响应式状态操作
            if (isUnmounted.value) return
            if (!loading.value) return
            updateMessage(sessionId, aiIndex, m => { m.streaming = false })
            loading.value = false
        },
        (error) => {
            // 组件已卸载（路由切换）时不弹错误，避免 "network error" 干扰
            if (isUnmounted.value) return
            if (!loading.value) return
            updateMessage(sessionId, aiIndex, m => {
                m.content = '抱歉，发生了错误：' + error
                m.streaming = false
            })
            loading.value = false
            message.error('对话出错: ' + error)
        },
        files,
        riskLevel
    )

    inputRef.value?.clearFiles()
}

function handleConfirm(confirmed) {
    handleSend(confirmed ? '确认' : '取消')
}

// 组件卸载时中止进行中的 SSE 请求，避免切换页面后 fetch 抛出 network error
onUnmounted(() => {
    isUnmounted.value = true
    if (abortController) {
        try { abortController.abort() } catch { /* ignore */ }
        abortController = null
    }
    loading.value = false
})
</script>

<style scoped>
.chat-view {
    height: 100%;
    background: #ffffff;
    display: flex;
    justify-content: center;
}
.chat-main {
    width: 100%;
    max-width: none;
    display: flex;
    flex-direction: column;
    height: 100%;
}
</style>
