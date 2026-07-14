<template>
    <AppLayout>
        <template #sidebar>
            <SessionSidebar
                :sessions="state.sessions"
                :current-id="state.currentId"
                @new-session="goChat"
                @select="goChat"
                @delete="goChat"
            />
        </template>

        <div class="settings-view">
            <div class="content">
                <h2 class="title">
                    <n-icon size="22"><SettingsOutline /></n-icon>
                    系统设置
                </h2>

                <!-- 风险偏好 -->
                <div class="card">
                    <div class="card-title">风险偏好</div>
                    <p class="card-desc">影响投资组合推荐的保守程度</p>
                    <n-radio-group
                        :value="riskLevelRef"
                        @update:value="handleRiskLevelChange"
                    >
                        <n-radio value="R1">R1 保守</n-radio>
                        <n-radio value="R2">R2 稳健</n-radio>
                        <n-radio value="R3">R3 平衡</n-radio>
                        <n-radio value="R4">R4 进取</n-radio>
                        <n-radio value="R5">R5 激进</n-radio>
                    </n-radio-group>
                </div>

                <!-- 显示设置 -->
                <div class="card">
                    <div class="card-title">显示设置</div>
                    <div class="setting-row">
                        <div>
                            <div class="setting-label">显示思考链</div>
                            <div class="setting-desc">AI 回复时默认展开推理过程</div>
                        </div>
                        <n-switch v-model:value="settings.showReasoning" />
                    </div>
                </div>

                <!-- 危险操作 -->
                <div class="card danger">
                    <div class="card-title">危险操作</div>
                    <div class="setting-row">
                        <div>
                            <div class="setting-label">清空所有会话</div>
                            <div class="setting-desc">删除所有本地保存的对话记录，此操作不可恢复</div>
                        </div>
                        <n-popconfirm
                            @positive-click="handleClearAll"
                            positive-text="确认清空"
                            negative-text="取消"
                        >
                            <template #trigger>
                                <n-button type="error" ghost>清空所有会话</n-button>
                            </template>
                            确定要清空所有 {{ state.sessions.length }} 个会话吗？此操作不可恢复。
                        </n-popconfirm>
                    </div>
                </div>

                <!-- 系统信息 -->
                <div class="card">
                    <div class="card-title">关于系统</div>
                    <n-descriptions :column="1" label-placement="left" bordered>
                        <n-descriptions-item label="框架版本">Spring AI Alibaba 1.1.2.2</n-descriptions-item>
                        <n-descriptions-item label="后端">Spring Boot 3.5.8 + JDK 17</n-descriptions-item>
                        <n-descriptions-item label="前端">Vue 3 + Naive UI</n-descriptions-item>
                        <n-descriptions-item label="大模型">DashScope (通义千问)</n-descriptions-item>
                        <n-descriptions-item label="向量存储">PostgreSQL pgvector</n-descriptions-item>
                    </n-descriptions>
                </div>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
    NIcon, NRadioGroup, NRadio, NSwitch, NButton,
    NPopconfirm, NDescriptions, NDescriptionsItem, useMessage
} from 'naive-ui'
import { SettingsOutline } from '@vicons/ionicons5'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { settings, resetSettings } from '../stores/settings.js'
import { auth } from '../stores/auth.js'
import { updateRiskLevel } from '../api/auth.js'
import { sessionsStore } from '../stores/sessions.js'

const { state, clearAll } = sessionsStore
const router = useRouter()
const message = useMessage()

const riskLevelRef = ref(settings.riskLevel)

onMounted(() => {
    if (auth.isLoggedIn && auth.user?.riskLevel) {
        settings.riskLevel = auth.user.riskLevel
        riskLevelRef.value = auth.user.riskLevel
    }
})

async function handleRiskLevelChange(newLevel) {
    const oldLevel = riskLevelRef.value
    riskLevelRef.value = newLevel
    try {
        await updateRiskLevel(newLevel)
        auth.setRiskLevel(newLevel)
        settings.riskLevel = newLevel
        message.success('风险等级已更新')
    } catch (e) {
        riskLevelRef.value = oldLevel
        message.error('更新失败')
    }
}

function handleClearAll() {
    clearAll()
    message.success('所有会话已清空')
    router.push('/chat')
}

function goChat() {
    router.push('/chat')
}
</script>

<style scoped>
.settings-view { height: 100%; overflow-y: auto; background: #ffffff; }
.content { max-width: 768px; margin: 0 auto; padding: 24px; }
.title { margin: 0 0 24px; display: flex; align-items: center; gap: 8px; font-size: 20px; color: #202123; font-weight: 600; }
.card { background: #ffffff; border: 1px solid #ececf1; border-radius: 8px; padding: 16px 20px; margin-bottom: 16px; }
.card.danger { border-color: #ffccc7; }
.card-title { font-size: 15px; font-weight: 600; color: #202123; margin-bottom: 4px; }
.card-desc { font-size: 12px; color: #6e6f80; margin: 0 0 12px; }
.setting-row { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; border-top: 1px solid #f7f7f8; }
.setting-row:first-of-type { border-top: none; }
.setting-label { font-size: 14px; color: #202123; }
.setting-desc { font-size: 12px; color: #6e6f80; margin-top: 2px; }
</style>
