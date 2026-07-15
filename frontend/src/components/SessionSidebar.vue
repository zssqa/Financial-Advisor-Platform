<template>
    <div class="session-sidebar">
        <div class="header">
            <a-button block type="primary" ghost @click="$emit('new-session')">
                <template #icon><PlusOutlined /></template>
                新建会话
            </a-button>
        </div>

        <div class="session-list">
            <div
                v-for="session in sessions"
                :key="session.id"
                class="session-item"
                :class="{ active: session.id === currentId }"
                @click="$emit('select', session.id)"
            >
                <MessageOutlined class="icon" style="font-size: 14px" />
                <span class="title">{{ session.title || '新会话' }}</span>
                <button class="del-btn" @click.stop="$emit('delete', session.id)">
                    <DeleteOutlined style="font-size: 14px" />
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
                <component :is="nav.icon" style="font-size: 16px" />
                <span>{{ nav.label }}</span>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { PlusOutlined, DeleteOutlined, MessageOutlined, AppstoreOutlined, SettingOutlined, CloudUploadOutlined, WalletOutlined, TrophyOutlined, RiseOutlined, BarChartOutlined, ToolOutlined } from '@ant-design/icons-vue'

const props = defineProps({
    sessions: { type: Array, default: () => [] },
    currentId: { type: String, default: null }
})

defineEmits(['new-session', 'select', 'delete'])

const router = useRouter()
const route = useRoute()
const currentPath = computed(() => route.path)

const navItems = [
    { path: '/chat', label: '智能对话', icon: MessageOutlined },
    { path: '/portfolio', label: '我的资产', icon: WalletOutlined },
    { path: '/goal', label: '理财目标', icon: TrophyOutlined },
    { path: '/market', label: '市场行情', icon: RiseOutlined },
    { path: '/analysis', label: '投资分析', icon: BarChartOutlined },
    { path: '/toolbox', label: '工具箱', icon: ToolOutlined },
    { path: '/knowledge', label: '知识库', icon: CloudUploadOutlined },
    { path: '/dashboard', label: '仪表盘', icon: AppstoreOutlined },
    { path: '/settings', label: '设置', icon: SettingOutlined }
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
