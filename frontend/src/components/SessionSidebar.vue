<template>
    <div class="session-sidebar">
        <div class="header">
            <n-button block type="primary" ghost @click="$emit('new-session')">
                <template #icon><n-icon><AddOutline /></n-icon></template>
                新建会话
            </n-button>
        </div>

        <div class="session-list">
            <div
                v-for="session in sessions"
                :key="session.id"
                class="session-item"
                :class="{ active: session.id === currentId }"
                @click="$emit('select', session.id)"
            >
                <n-icon size="14" class="icon"><ChatbubbleEllipsesOutline /></n-icon>
                <span class="title">{{ session.title || '新会话' }}</span>
                <button class="del-btn" @click.stop="$emit('delete', session.id)">
                    <n-icon size="14"><TrashOutline /></n-icon>
                </button>
            </div>
            <div v-if="sessions.length === 0" class="empty">
                暂无会话
            </div>
        </div>

        <div class="footer">
            <div
                v-for="nav in navItems"
                :key="nav.path"
                class="nav-item"
                :class="{ active: currentPath === nav.path }"
                @click="goTo(nav.path)"
            >
                <n-icon size="16"><component :is="nav.icon" /></n-icon>
                <span>{{ nav.label }}</span>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NButton, NIcon } from 'naive-ui'
import { AddOutline, TrashOutline, ChatbubbleEllipsesOutline, GridOutline, SettingsOutline, CloudUploadOutline, WalletOutline, TrophyOutline } from '@vicons/ionicons5'

const props = defineProps({
    sessions: { type: Array, default: () => [] },
    currentId: { type: String, default: null }
})

defineEmits(['new-session', 'select', 'delete'])

const router = useRouter()
const route = useRoute()
const currentPath = computed(() => route.path)

const navItems = [
    { path: '/chat', label: '智能对话', icon: ChatbubbleEllipsesOutline },
    { path: '/portfolio', label: '我的资产', icon: WalletOutline },
    { path: '/goal', label: '理财目标', icon: TrophyOutline },
    { path: '/knowledge', label: '知识库', icon: CloudUploadOutline },
    { path: '/dashboard', label: '仪表盘', icon: GridOutline },
    { path: '/settings', label: '设置', icon: SettingsOutline }
]

function goTo(path) {
    router.push(path)
}
</script>

<style scoped>
.session-sidebar { display: flex; flex-direction: column; height: 100%; }
.header { padding: 12px; border-bottom: 1px solid rgba(255,255,255,0.1); }
.session-list { flex: 1; overflow-y: auto; padding: 8px; }
.session-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    margin-bottom: 2px;
    border-radius: 6px;
    cursor: pointer;
    color: #ececf1;
    font-size: 13px;
    transition: background 0.15s;
    position: relative;
}
.session-item:hover { background: rgba(255,255,255,0.05); }
.session-item.active { background: rgba(255,255,255,0.1); }
.session-item .icon { opacity: 0.6; flex-shrink: 0; }
.session-item .title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.del-btn {
    background: transparent;
    border: none;
    color: rgba(255,255,255,0.4);
    cursor: pointer;
    padding: 4px;
    border-radius: 4px;
    display: none;
}
.session-item:hover .del-btn { display: flex; }
.del-btn:hover { background: rgba(255,255,255,0.1); color: #ff4d4f; }
.empty { padding: 40px 12px; text-align: center; color: rgba(255,255,255,0.3); font-size: 12px; }
.footer { border-top: 1px solid rgba(255,255,255,0.1); padding: 8px; }
.nav-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    border-radius: 6px;
    cursor: pointer;
    color: #ececf1;
    font-size: 13px;
    transition: background 0.15s;
}
.nav-item:hover { background: rgba(255,255,255,0.05); }
.nav-item.active { background: rgba(255,255,255,0.1); }
</style>
