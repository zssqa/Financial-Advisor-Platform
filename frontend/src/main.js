import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ChatView from './views/ChatView.vue'
import KnowledgeView from './views/KnowledgeView.vue'
import DashboardView from './views/DashboardView.vue'
import SettingsView from './views/SettingsView.vue'
import LoginView from './views/LoginView.vue'
import PortfolioView from './views/PortfolioView.vue'
import GoalView from './views/GoalView.vue'
import MarketView from './views/MarketView.vue'
import AnalysisView from './views/AnalysisView.vue'
import ToolboxView from './views/ToolboxView.vue'
import { auth } from './stores/auth.js'

const routes = [
    { path: '/', redirect: '/chat' },
    { path: '/login', name: 'Login', component: LoginView, meta: { title: '登录', guest: true } },
    { path: '/chat', name: 'Chat', component: ChatView, meta: { title: '智能对话', requiresAuth: true } },
    { path: '/knowledge', name: 'Knowledge', component: KnowledgeView, meta: { title: '知识库', requiresAuth: true } },
    { path: '/dashboard', name: 'Dashboard', component: DashboardView, meta: { title: '仪表盘', requiresAuth: true } },
    { path: '/settings', name: 'Settings', component: SettingsView, meta: { title: '设置', requiresAuth: true } },
    { path: '/portfolio', name: 'Portfolio', component: PortfolioView, meta: { title: '资产管理', requiresAuth: true } },
    { path: '/goal', name: 'Goal', component: GoalView, meta: { title: '理财目标', requiresAuth: true } },
    { path: '/market', name: 'Market', component: MarketView, meta: { title: '市场行情', requiresAuth: true } },
    { path: '/analysis', name: 'Analysis', component: AnalysisView, meta: { title: '投资分析', requiresAuth: true } },
    { path: '/toolbox', name: 'Toolbox', component: ToolboxView, meta: { title: '工具箱', requiresAuth: true } }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    if (to.meta.requiresAuth && !auth.isLoggedIn) {
        next('/login?redirect=' + encodeURIComponent(to.fullPath))
    } else if (to.meta.guest && auth.isLoggedIn) {
        next('/chat')
    } else {
        next()
    }
})

router.afterEach((to) => {
    document.title = to.meta.title ? `${to.meta.title} - 金融理财顾问` : '金融理财顾问'
})

const app = createApp(App)
app.use(router)
app.mount('#app')
