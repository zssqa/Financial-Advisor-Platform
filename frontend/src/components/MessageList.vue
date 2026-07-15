<template>
    <div ref="scrollContainer" class="message-list">
        <div v-if="messages.length === 0" class="empty">
            <div class="empty-icon">💰</div>
            <h2>您好！我是金融理财顾问</h2>
            <p>我可以帮您进行理财规划、风险评估和产品分析</p>
            <div class="suggestions">
                <button
                    v-for="s in suggestions"
                    :key="s"
                    class="suggestion"
                    @click="$emit('send', s)"
                >{{ s }}</button>
            </div>
        </div>

        <div v-else class="messages">
            <div v-for="(msg, idx) in messages" :key="idx" class="msg-row">
                <div v-if="msg.role === 'user'" class="user-row">
                    <div class="user-bubble">{{ msg.content }}</div>
                </div>

                <div v-else class="ai-row">
                    <div class="avatar">AI</div>
                    <div class="ai-content">
                        <div v-if="msg.reasoning" class="reasoning">
                            <button class="reasoning-toggle" @click="msg.showReasoning = !msg.showReasoning">
                                <span>{{ msg.showReasoning ? '▾' : '▸' }} 查看推理过程</span>
                            </button>
                            <div v-if="msg.showReasoning" class="reasoning-body">{{ msg.reasoning }}</div>
                        </div>

                        <div v-if="msg.toolCalls && msg.toolCalls.length" class="tools">
                            <div
                                v-for="(tc, tci) in msg.toolCalls"
                                :key="tci"
                                class="tool-chip"
                                :class="tc.status"
                            >
                                <span v-if="tc.status === 'running'" class="spinner"></span>
                                <span v-else-if="tc.status === 'done'" class="check">✓</span>
                                <span class="tool-name">{{ tc.tool }}</span>
                                <span v-if="tc.status === 'running'" class="status-text">调用中...</span>
                                <span v-else-if="tc.result" class="tool-result">{{ tc.result }}</span>
                            </div>
                        </div>

                        <div v-if="msg.streaming" class="streaming-text">
                            <span>{{ msg.content }}</span><span class="cursor">▍</span>
                        </div>
                        <MarkdownContent v-else :content="msg.content" />

                        <div v-if="msg.showConfirmation" class="confirm-row">
                            <a-button size="small" type="primary" @click="$emit('confirm', true)">确认执行</a-button>
                            <a-button size="small" @click="$emit('confirm', false)">取消</a-button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import MarkdownContent from './MarkdownContent.vue'

const props = defineProps({
    messages: { type: Array, default: () => [] },
    suggestions: { type: Array, default: () => [] },
    streaming: { type: Boolean, default: false }
})

defineEmits(['send', 'confirm'])

const scrollContainer = ref(null)

function scrollToBottom() {
    nextTick(() => {
        if (scrollContainer.value) {
            scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
        }
    })
}

watch(() => props.messages.length, scrollToBottom)
watch(() => props.messages, scrollToBottom, { deep: true })

defineExpose({ scrollToBottom })
</script>

<style scoped>
.message-list { flex: 1; overflow-y: auto; padding: 24px 16px; }
.empty { text-align: center; padding: 60px 20px; color: #565869; }
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty h2 { margin: 0 0 8px; font-size: 26px; color: #202123; font-weight: 600; }
.empty p { margin: 0 0 24px; color: #6e6f80; font-size: 18px; }
.suggestions { display: flex; flex-direction: column; gap: 8px; max-width: 360px; margin: 0 auto; }
.suggestion {
    padding: 12px 16px;
    background: #f7f7f8;
    border: 1px solid #e5e5e5;
    border-radius: 8px;
    cursor: pointer;
    text-align: left;
    font-size: 16px;
    color: #202123;
    transition: background 0.15s;
}
.suggestion:hover { background: #ececf1; }
.messages { display: flex; flex-direction: column; gap: 16px; }
.msg-row { width: 100%; }
.user-row { display: flex; justify-content: flex-end; }
.user-bubble {
    max-width: 70%;
    background: #f0f0f0;
    color: #202123;
    padding: 12px 18px;
    border-radius: 16px;
    font-size: 18px;
    line-height: 1.9;
    white-space: pre-wrap;
    word-break: break-word;
}
.ai-row { display: flex; gap: 12px; align-items: flex-start; }
.avatar {
    width: 28px; height: 28px;
    border-radius: 50%;
    background: #10a37f;
    color: white;
    display: flex; align-items: center; justify-content: center;
    font-size: 11px; font-weight: 600;
    flex-shrink: 0;
}
.ai-content { flex: 1; min-width: 0; font-size: 18px; line-height: 1.9; }
.reasoning { margin-bottom: 8px; }
.reasoning-toggle {
    background: transparent; border: none; cursor: pointer;
    color: #6e6f80; font-size: 12px; padding: 4px 0;
}
.reasoning-toggle:hover { color: #202123; }
.reasoning-body {
    margin-top: 4px; padding: 8px 12px;
    background: #f7f7f8; border-radius: 6px;
    font-size: 16px; color: #565869; line-height: 1.7;
    white-space: pre-wrap;
}
.tools { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.tool-chip {
    display: inline-flex; align-items: center; gap: 6px;
    padding: 5px 12px; border-radius: 12px;
    font-size: 15px; max-width: 100%;
}
.tool-chip.running { background: #fff7e6; color: #d46b08; }
.tool-chip.done { background: #f0f9eb; color: #389e0d; }
.spinner {
    width: 10px; height: 10px;
    border: 1.5px solid currentColor;
    border-top-color: transparent;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.check { font-weight: 600; }
.tool-name { font-weight: 500; }
.status-text { opacity: 0.7; }
.tool-result {
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    max-width: 200px; opacity: 0.7;
}
.streaming-text {
    font-size: 18px; line-height: 1.9; white-space: pre-wrap; word-break: break-word;
    color: #202123;
}
.cursor {
    display: inline-block;
    color: #10a37f;
    animation: blink 1s steps(2) infinite;
}
@keyframes blink { 50% { opacity: 0; } }
.confirm-row { margin-top: 12px; display: flex; gap: 8px; }
</style>
