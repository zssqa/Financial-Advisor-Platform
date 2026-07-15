<template>
    <div class="layout">
        <!-- 桌面侧边栏 -->
        <aside v-show="!isMobile" class="sidebar" :class="{ collapsed: !sidebarOpen }">
            <div class="sidebar-inner">
                <slot name="sidebar" />
            </div>
        </aside>

        <!-- 移动端浮层 -->
        <transition name="fade">
            <div v-if="isMobile && sidebarOpen" class="mobile-overlay" @click="sidebarOpen = false">
                <aside class="sidebar mobile" @click.stop>
                    <div class="sidebar-inner">
                        <slot name="sidebar" />
                    </div>
                </aside>
            </div>
        </transition>

        <!-- 主区 -->
        <main class="main">
            <button v-if="isMobile || !sidebarOpen" class="toggle-btn" @click="toggleSidebar">
                <MenuOutlined v-if="!sidebarOpen" style="font-size: 20px" />
                <CloseOutlined v-else style="font-size: 20px" />
            </button>

            <header v-if="auth.isLoggedIn" class="top-bar">
                <div class="user-box">
                    <UserOutlined style="font-size: 16px" />
                    <span class="username">{{ auth.user?.username }}</span>
                </div>
                <a-button size="small" type="text" danger @click="handleLogout">
                    <template #icon>
                        <LogoutOutlined />
                    </template>
                    退出登录
                </a-button>
            </header>

            <div class="main-content">
                <slot />
            </div>
        </main>
    </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { MenuOutlined, CloseOutlined, UserOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { auth } from '../stores/auth.js'
import { getProfile } from '../api/auth.js'

const router = useRouter()
const sidebarOpen = ref(true)
const isMobile = ref(false)

function checkViewport() {
    isMobile.value = window.innerWidth < 768
    if (isMobile.value) sidebarOpen.value = false
}

function toggleSidebar() {
    sidebarOpen.value = !sidebarOpen.value
}

function handleLogout() {
    auth.clear()
    router.push('/login')
}

// 登录态下从 /api/auth/profile 同步最新 riskLevel（DB 为准），避免本地缓存过期
async function syncProfile() {
    if (!auth.isLoggedIn) return
    try {
        const profile = await getProfile()
        if (profile && auth.user) {
            auth.user.userId = profile.userId
            auth.user.username = profile.username
            auth.user.riskLevel = profile.riskLevel
        }
    } catch {
        // 静默失败，不影响页面渲染
    }
}

onMounted(() => {
    checkViewport()
    window.addEventListener('resize', checkViewport)
    syncProfile()
})

onUnmounted(() => {
    window.removeEventListener('resize', checkViewport)
})
</script>

<style scoped>
.layout { display: flex; height: 100vh; width: 100%; }
.sidebar {
    width: 260px;
    background: #202123;
    color: #ececf1;
    transition: width 0.2s ease, transform 0.2s ease;
    flex-shrink: 0;
    overflow: hidden;
}
.sidebar.collapsed { width: 0; }
.sidebar.mobile {
    position: fixed;
    top: 0; left: 0; bottom: 0;
    z-index: 1000;
    width: 260px;
}
.sidebar-inner { width: 260px; height: 100%; overflow-y: auto; }
.main {
    flex: 1;
    background: #ffffff;
    position: relative;
    overflow: hidden;
    min-width: 0;
    display: flex;
    flex-direction: column;
}
.toggle-btn {
    position: absolute;
    top: 12px; left: 12px;
    z-index: 10;
    background: transparent;
    border: none;
    cursor: pointer;
    padding: 8px;
    border-radius: 6px;
    color: #565869;
    display: flex;
    align-items: center;
}
.toggle-btn:hover { background: #f0f0f0; }
.top-bar {
    height: 48px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px 0 56px;
    border-bottom: 1px solid #ececf1;
    background: #ffffff;
}
.user-box {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #565869;
}
.username {
    font-size: 14px;
    color: #202123;
    font-weight: 500;
}
.main-content {
    flex: 1;
    overflow: hidden;
    min-height: 0;
}
.mobile-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.5);
    z-index: 999;
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
