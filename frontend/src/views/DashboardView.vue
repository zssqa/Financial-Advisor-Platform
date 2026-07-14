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

        <div class="dashboard-view">
            <div class="content">
                <h2 class="title">
                    <n-icon size="22"><GridOutline /></n-icon>
                    个人看板
                </h2>

                <!-- 净资产概览 -->
                <div class="stats">
                    <div class="stat-card">
                        <div class="stat-value">{{ formatMoney(portfolio.totalCost) }}</div>
                        <div class="stat-label">总资产成本</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{{ formatMoney(portfolio.totalMarketValue) }}</div>
                        <div class="stat-label">总市值</div>
                    </div>
                    <div class="stat-card">
                        <div
                            class="stat-value"
                            :style="{ color: profitLossColor }"
                        >
                            {{ formatMoney(portfolio.profitLoss) }}
                        </div>
                        <div class="stat-label">累计盈亏</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{{ goalCount }}</div>
                        <div class="stat-label">理财目标数</div>
                    </div>
                </div>

                <div class="grid">
                    <!-- 资产配置饼图 -->
                    <div class="card">
                        <div class="card-header">
                            <span>资产配置</span>
                            <n-button text type="primary" size="small" @click="goPortfolio">管理资产</n-button>
                        </div>
                        <n-spin :show="loadingPortfolio">
                            <div v-if="hasAssets" class="chart-wrap">
                                <Pie :data="chartData" :options="chartOptions" />
                            </div>
                            <n-empty v-else description="暂无资产，去添加" size="small">
                                <template #extra>
                                    <n-button size="small" type="primary" @click="goPortfolio">前往添加</n-button>
                                </template>
                            </n-empty>
                        </n-spin>
                    </div>

                    <!-- 理财目标进度 -->
                    <div class="card">
                        <div class="card-header">
                            <span>理财目标进度</span>
                            <n-button text type="primary" size="small" @click="goGoal">管理目标</n-button>
                        </div>
                        <n-spin :show="loadingGoal">
                            <div v-if="hasGoals" class="goal-list">
                                <div v-for="(g, idx) in goalSummary.goals" :key="idx" class="goal-row">
                                    <div class="goal-info">
                                        <span class="goal-type">{{ goalTypeLabel(g) }}</span>
                                        <span class="goal-amount">目标 {{ formatMoney(g.goal?.targetAmount) }}</span>
                                    </div>
                                    <n-progress
                                        type="line"
                                        :percentage="g.progressPercent || 0"
                                        :status="goalStatus(g.progressPercent)"
                                        :show-indicator="true"
                                    />
                                    <div class="goal-meta">
                                        <span>剩余 {{ formatMoney(g.remainingAmount) }}</span>
                                        <span>{{ g.monthsRemaining || 0 }} 个月 · 月需 {{ formatMoney(g.monthlyNeeded) }}</span>
                                    </div>
                                </div>
                            </div>
                            <n-empty v-else description="暂无理财目标，去设置" size="small">
                                <template #extra>
                                    <n-button size="small" type="primary" @click="goGoal">前往设置</n-button>
                                </template>
                            </n-empty>
                        </n-spin>
                    </div>

                    <!-- 最近 AI 建议 / 会话 -->
                    <div class="card full">
                        <div class="card-header">
                            <span>最近会话</span>
                            <n-button text type="primary" size="small" @click="goChat">查看全部</n-button>
                        </div>
                        <div v-if="recentSessions.length > 0">
                            <div
                                v-for="session in recentSessions"
                                :key="session.id"
                                class="session-row"
                                @click="openSession(session.id)"
                            >
                                <n-icon size="14" color="#6e6f80"><ChatbubbleEllipsesOutline /></n-icon>
                                <div class="session-info">
                                    <div class="session-title">{{ session.title || '新会话' }}</div>
                                    <div class="session-meta">
                                        {{ formatTime(session.updatedAt) }} · {{ session.messages?.length || 0 }} 条消息
                                    </div>
                                </div>
                            </div>
                        </div>
                        <n-empty v-else description="还没有对话，开始第一次交流吧" size="small">
                            <template #extra>
                                <n-button size="small" type="primary" @click="goChat">开始对话</n-button>
                            </template>
                        </n-empty>
                    </div>
                </div>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
    NIcon, NButton, NProgress, NEmpty, NSpin
} from 'naive-ui'
import { GridOutline, ChatbubbleEllipsesOutline } from '@vicons/ionicons5'
import { Pie } from 'vue-chartjs'
import {
    Chart as ChartJS, ArcElement, Tooltip, Legend
} from 'chart.js'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { sessionsStore } from '../stores/sessions.js'
import { getSummary as getPortfolioSummary } from '../api/portfolio.js'
import { getSummary as getGoalSummary } from '../api/goal.js'

ChartJS.register(ArcElement, Tooltip, Legend)

const { state, selectSession } = sessionsStore
const router = useRouter()

const portfolio = ref({
    totalCost: 0,
    totalMarketValue: 0,
    profitLoss: 0,
    breakdown: []
})
const goalSummary = ref({ goals: [] })

const loadingPortfolio = ref(true)
const loadingGoal = ref(true)

const TYPE_LABEL_MAP = {
    stock: '股票',
    fund: '基金',
    deposit: '存款',
    bond: '债券',
    cash: '现金',
    other: '其他'
}

// 理财目标类型中文标签（与 GoalView 保持一致）
const GOAL_TYPE_LABELS = {
    retirement: '退休',
    education: '教育',
    house: '购房',
    emergency_fund: '应急基金',
    custom: '自定义'
}

function goalTypeLabel(g) {
    if (!g?.goal) return '未命名目标'
    return GOAL_TYPE_LABELS[g.goal.type] || g.goal.notes || '未命名目标'
}

const TYPE_COLOR_MAP = {
    stock: '#10a37f',
    fund: '#1890ff',
    deposit: '#fa8c16',
    bond: '#722ed1',
    cash: '#a0a0b0',
    other: '#13c2c2'
}

const DEFAULT_COLORS = ['#10a37f', '#1890ff', '#fa8c16', '#722ed1', '#a0a0b0', '#13c2c2', '#eb2f96', '#52c41a']

const hasAssets = computed(() =>
    Array.isArray(portfolio.value.breakdown) && portfolio.value.breakdown.length > 0
)

const hasGoals = computed(() =>
    Array.isArray(goalSummary.value.goals) && goalSummary.value.goals.length > 0
)

const goalCount = computed(() =>
    Array.isArray(goalSummary.value.goals) ? goalSummary.value.goals.length : 0
)

const profitLossColor = computed(() => {
    const v = Number(portfolio.value.profitLoss || 0)
    if (v > 0) return '#10a37f'
    if (v < 0) return '#ff4d4f'
    return '#202123'
})

const chartData = computed(() => {
    const breakdown = portfolio.value.breakdown || []
    const labels = breakdown.map(item => TYPE_LABEL_MAP[item.type] || item.type || '其他')
    const data = breakdown.map(item => Number(item.marketValue || 0))
    const backgroundColor = breakdown.map((item, idx) => {
        const key = (item.type || '').toLowerCase()
        return TYPE_COLOR_MAP[key] || DEFAULT_COLORS[idx % DEFAULT_COLORS.length]
    })
    return {
        labels,
        datasets: [{ data, backgroundColor, borderWidth: 1 }]
    }
})

const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
        legend: {
            position: 'bottom',
            labels: { font: { size: 12 }, boxWidth: 12, padding: 10 }
        },
        tooltip: {
            callbacks: {
                label(ctx) {
                    const val = ctx.parsed
                    const total = ctx.dataset.data.reduce((a, b) => a + Number(b || 0), 0)
                    const pct = total > 0 ? ((val / total) * 100).toFixed(1) : '0.0'
                    return `${ctx.label}: ${formatMoney(val)} (${pct}%)`
                }
            }
        }
    }
}

const recentSessions = computed(() =>
    [...state.sessions]
        .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
        .slice(0, 3)
)

function formatMoney(v) {
    const n = Number(v || 0)
    return '¥' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTime(ts) {
    if (!ts) return '-'
    const d = new Date(ts)
    const now = Date.now()
    const diff = now - d.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
    if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
    if (diff < 604800000) return Math.floor(diff / 86400000) + ' 天前'
    return d.toLocaleDateString()
}

function goalStatus(percent) {
    const p = Number(percent || 0)
    if (p >= 100) return 'success'
    if (p < 30) return 'warning'
    return 'default'
}

function openSession(id) {
    selectSession(id)
    router.push('/chat')
}

function goChat() {
    router.push('/chat')
}

function goPortfolio() {
    router.push('/portfolio')
}

function goGoal() {
    router.push('/goal')
}

async function loadPortfolio() {
    loadingPortfolio.value = true
    try {
        const data = await getPortfolioSummary()
        portfolio.value = {
            totalCost: Number(data?.totalCost || 0),
            totalMarketValue: Number(data?.totalMarketValue || 0),
            profitLoss: Number(data?.profitLoss || 0),
            breakdown: Array.isArray(data?.breakdown) ? data.breakdown : []
        }
    } catch {
        portfolio.value = { totalCost: 0, totalMarketValue: 0, profitLoss: 0, breakdown: [] }
    } finally {
        loadingPortfolio.value = false
    }
}

async function loadGoal() {
    loadingGoal.value = true
    try {
        const data = await getGoalSummary()
        goalSummary.value = {
            goals: Array.isArray(data?.goals) ? data.goals : []
        }
    } catch {
        goalSummary.value = { goals: [] }
    } finally {
        loadingGoal.value = false
    }
}

onMounted(() => {
    Promise.allSettled([loadPortfolio(), loadGoal()])
})
</script>

<style scoped>
.dashboard-view { height: 100%; overflow-y: auto; background: #ffffff; }
.content { max-width: 1024px; margin: 0 auto; padding: 24px; }
.title { margin: 0 0 24px; display: flex; align-items: center; gap: 8px; font-size: 20px; color: #202123; font-weight: 600; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px; }
.stat-card { padding: 20px; background: #f7f7f8; border-radius: 8px; text-align: center; }
.stat-value { font-size: 22px; font-weight: 700; word-break: break-all; }
.stat-label { font-size: 12px; color: #6e6f80; margin-top: 4px; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.card { background: #ffffff; border: 1px solid #ececf1; border-radius: 8px; padding: 16px; }
.card.full { grid-column: 1 / -1; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-weight: 600; color: #202123; font-size: 14px; }
.chart-wrap { height: 260px; position: relative; }
.goal-list { display: flex; flex-direction: column; gap: 14px; }
.goal-row { display: flex; flex-direction: column; gap: 6px; padding: 8px 0; border-bottom: 1px solid #f7f7f8; }
.goal-row:last-child { border-bottom: none; }
.goal-info { display: flex; justify-content: space-between; align-items: center; }
.goal-type { font-size: 13px; color: #202123; font-weight: 500; }
.goal-amount { font-size: 12px; color: #6e6f80; }
.goal-meta { display: flex; justify-content: space-between; font-size: 11px; color: #a0a0b0; }
.session-row { display: flex; align-items: center; gap: 10px; padding: 10px 8px; border-radius: 6px; cursor: pointer; transition: background 0.15s; }
.session-row:hover { background: #f7f7f8; }
.session-info { flex: 1; min-width: 0; }
.session-title { font-size: 13px; color: #202123; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-meta { font-size: 11px; color: #a0a0b0; margin-top: 2px; }
@media (max-width: 768px) {
    .stats { grid-template-columns: repeat(2, 1fr); }
    .grid { grid-template-columns: 1fr; }
    .card.full { grid-column: auto; }
}
</style>
