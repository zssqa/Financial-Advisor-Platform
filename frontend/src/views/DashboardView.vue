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
                    <AppstoreOutlined :style="{ fontSize: '22px' }" />
                    个人看板
                    <!-- 价格预警红点入口：未读数 > 0 时显示红色徽章，点击跳转资产管理页 -->
                    <a-badge
                        v-if="unreadAlerts > 0"
                        :count="unreadAlerts"
                        :overflowCount="99"
                        class="alert-badge"
                    >
                        <a-button type="text" size="small" @click="goPortfolio">
                            <template #icon>
                                <BellOutlined :style="{ color: '#ff4d4f' }" />
                            </template>
                            预警
                        </a-button>
                    </a-badge>
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

                <!-- 盈亏趋势折线图：近 30 天总市值变化 -->
                <div class="card trend-card">
                    <div class="card-header">
                        <span>近 30 天市值趋势</span>
                    </div>
                    <a-spin :spinning="loadingHistory">
                        <div v-if="hasHistory" class="trend-wrap">
                            <Line :data="historyChartData" :options="historyChartOptions" />
                        </div>
                        <a-empty v-else description="暂无历史数据" />
                    </a-spin>
                </div>

                <div class="grid">
                    <!-- 资产配置饼图 -->
                    <div class="card">
                        <div class="card-header">
                            <span>资产配置</span>
                            <a-button type="link" size="small" @click="goPortfolio">管理资产</a-button>
                        </div>
                        <a-spin :spinning="loadingPortfolio">
                            <div v-if="hasAssets" class="chart-wrap">
                                <Pie :data="chartData" :options="chartOptions" />
                            </div>
                            <a-empty v-else description="暂无资产，去添加">
                                <a-button size="small" type="primary" @click="goPortfolio">前往添加</a-button>
                            </a-empty>
                        </a-spin>
                    </div>

                    <!-- 理财目标进度 -->
                    <div class="card">
                        <div class="card-header">
                            <span>理财目标进度</span>
                            <a-button type="link" size="small" @click="goGoal">管理目标</a-button>
                        </div>
                        <a-spin :spinning="loadingGoal">
                            <div v-if="hasGoals" class="goal-list">
                                <div v-for="(g, idx) in goalSummary.goals" :key="idx" class="goal-row">
                                    <div class="goal-info">
                                        <span class="goal-type">{{ goalTypeLabel(g) }}</span>
                                        <span class="goal-amount">目标 {{ formatMoney(g.goal?.targetAmount) }}</span>
                                    </div>
                                    <a-progress
                                        type="line"
                                        :percent="g.progressPercent || 0"
                                        :status="goalStatus(g.progressPercent)"
                                        :showInfo="true"
                                    />
                                    <div class="goal-meta">
                                        <span>剩余 {{ formatMoney(g.remainingAmount) }}</span>
                                        <span>{{ g.monthsRemaining || 0 }} 个月 · 月需 {{ formatMoney(g.monthlyNeeded) }}</span>
                                    </div>
                                </div>
                            </div>
                            <a-empty v-else description="暂无理财目标，去设置">
                                <a-button size="small" type="primary" @click="goGoal">前往设置</a-button>
                            </a-empty>
                        </a-spin>
                    </div>

                    <!-- 最近 AI 建议 / 会话 -->
                    <div class="card full">
                        <div class="card-header">
                            <span>最近会话</span>
                            <a-button type="link" size="small" @click="goChat">查看全部</a-button>
                        </div>
                        <div v-if="recentSessions.length > 0">
                            <div
                                v-for="session in recentSessions"
                                :key="session.id"
                                class="session-row"
                                @click="openSession(session.id)"
                            >
                                <MessageOutlined :style="{ fontSize: '14px', color: '#6e6f80' }" />
                                <div class="session-info">
                                    <div class="session-title">{{ session.title || '新会话' }}</div>
                                    <div class="session-meta">
                                        {{ formatTime(session.updatedAt) }} · {{ session.messages?.length || 0 }} 条消息
                                    </div>
                                </div>
                            </div>
                        </div>
                        <a-empty v-else description="还没有对话，开始第一次交流吧">
                            <a-button size="small" type="primary" @click="goChat">开始对话</a-button>
                        </a-empty>
                    </div>
                </div>

                <!-- AI 资产配置建议 -->
                <a-card class="optimize-card" size="small">
                    <template #title>
                        <span class="optimize-title">AI 资产配置建议</span>
                    </template>
                    <template #extra>
                        <a-tag color="blue">智能优化</a-tag>
                    </template>
                    <a-spin :spinning="loadingOptimize">
                        <div v-if="optimize" class="optimize-content">
                            <div class="optimize-weights">
                                <div
                                    v-for="item in weightsAsPercent"
                                    :key="item.type"
                                    class="weight-row"
                                >
                                    <div class="weight-label">{{ TYPE_LABEL_MAP[item.type] || item.type }}</div>
                                    <a-progress
                                        type="line"
                                        :percent="item.percent"
                                        :showInfo="true"
                                    />
                                </div>
                            </div>
                            <div class="optimize-metrics">
                                <div class="metric">
                                    <div class="metric-value">{{ formatPercent(optimize.expectedReturn) }}</div>
                                    <div class="metric-label">预期收益</div>
                                </div>
                                <div class="metric">
                                    <div class="metric-value">{{ formatPercent(optimize.volatility) }}</div>
                                    <div class="metric-label">波动率</div>
                                </div>
                                <div class="metric">
                                    <div class="metric-value">{{ formatNumber(optimize.sharpeRatio) }}</div>
                                    <div class="metric-label">夏普比率</div>
                                </div>
                            </div>
                        </div>
                        <a-empty v-else description="暂无优化建议" />
                    </a-spin>
                </a-card>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { AppstoreOutlined, MessageOutlined, BellOutlined } from '@ant-design/icons-vue'
import { Pie, Line } from 'vue-chartjs'
import {
    Chart as ChartJS, ArcElement, Tooltip, Legend,
    CategoryScale, LinearScale, PointElement, LineElement, Filler
} from 'chart.js'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { sessionsStore } from '../stores/sessions.js'
import { getSummary as getPortfolioSummary, getPortfolioHistory } from '../api/portfolio.js'
import { getSummary as getGoalSummary } from '../api/goal.js'
import { getOptimize } from '../api/analysis.js'

ChartJS.register(ArcElement, Tooltip, Legend, CategoryScale, LinearScale, PointElement, LineElement, Filler)

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
const loadingHistory = ref(true)
const loadingOptimize = ref(true)

// 盈亏趋势历史数据
const history = ref([])
// AI 资产配置优化建议
const optimize = ref(null)
// 未读价格预警数（来自 portfolio summary）
const unreadAlerts = ref(0)

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

// 是否有历史趋势数据
const hasHistory = computed(() => Array.isArray(history.value) && history.value.length > 0)

// 盈亏趋势折线图数据
const historyChartData = computed(() => {
    const points = history.value || []
    const labels = points.map(p => formatDateLabel(p.date || p.timestamp || p.time))
    const data = points.map(p => Number(p.totalMarketValue ?? p.marketValue ?? p.value ?? 0))
    return {
        labels,
        datasets: [{
            label: '总市值',
            data,
            borderColor: '#10a37f',
            backgroundColor: 'rgba(16, 163, 127, 0.12)',
            fill: true,
            tension: 0.3,
            pointRadius: 0,
            pointHoverRadius: 4,
            borderWidth: 2
        }]
    }
})

// 盈亏趋势折线图配置
const historyChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
        legend: { display: false },
        tooltip: {
            callbacks: {
                label(ctx) {
                    return `总市值: ${formatMoney(ctx.parsed.y)}`
                }
            }
        }
    },
    scales: {
        x: {
            grid: { display: false },
            ticks: { font: { size: 11 }, maxRotation: 0, autoSkip: true, maxTicksLimit: 8 }
        },
        y: {
            ticks: { font: { size: 11 }, callback: (v) => formatMoneyShort(v) }
        }
    }
}

// 归一化 AI 推荐权重为 [{ type, weight }]，兼容数组/对象两种返回
const optimizeWeights = computed(() => {
    const w = optimize.value?.weights
    if (!w) return []
    let arr = []
    if (Array.isArray(w)) {
        arr = w.map(item => ({
            type: item.type || item.assetType || item.name || 'other',
            weight: Number(item.weight ?? item.ratio ?? 0)
        }))
    } else if (typeof w === 'object') {
        arr = Object.entries(w).map(([type, weight]) => ({
            type,
            weight: Number(weight)
        }))
    }
    return arr.filter(item => item.weight > 0).sort((a, b) => b.weight - a.weight)
})

// 将权重转换为百分比（兼容小数 0.4 与已为百分比的 40 两种形态）
const weightsAsPercent = computed(() => {
    const arr = optimizeWeights.value
    const sum = arr.reduce((a, b) => a + b.weight, 0)
    if (sum <= 0) return []
    const multiplier = sum <= 1.5 ? 100 : 1
    return arr.map(item => ({ ...item, percent: Number((item.weight * multiplier).toFixed(2)) }))
})

function formatMoney(v) {
    const n = Number(v || 0)
    return '¥' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 折线图日期标签：月/日
function formatDateLabel(d) {
    if (!d) return ''
    const date = new Date(d)
    if (isNaN(date.getTime())) return String(d)
    return `${date.getMonth() + 1}/${date.getDate()}`
}

// 折线图 Y 轴简短金额（万）
function formatMoneyShort(v) {
    const n = Number(v || 0)
    if (Math.abs(n) >= 10000) return (n / 10000).toFixed(1) + '万'
    return n.toLocaleString('zh-CN')
}

// 百分比：兼容小数(0.08)与已是百分比的数值(8.0)
function formatPercent(v) {
    const n = Number(v || 0)
    const pct = Math.abs(n) <= 1 ? n * 100 : n
    return pct.toFixed(2) + '%'
}

// 普通数值（夏普比率等）
function formatNumber(v) {
    const n = Number(v || 0)
    return n.toFixed(2)
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
        unreadAlerts.value = Number(data?.unreadAlerts || 0)
    } catch {
        portfolio.value = { totalCost: 0, totalMarketValue: 0, profitLoss: 0, breakdown: [] }
        unreadAlerts.value = 0
    } finally {
        loadingPortfolio.value = false
    }
}

// 加载近 30 天市值趋势
async function loadHistory() {
    loadingHistory.value = true
    try {
        const data = await getPortfolioHistory(30)
        // 兼容数组或 { points: [...] } 两种返回
        history.value = Array.isArray(data) ? data : (Array.isArray(data?.points) ? data.points : [])
    } catch {
        history.value = []
    } finally {
        loadingHistory.value = false
    }
}

// 加载 AI 资产配置优化建议
// 后端返回结构化 JSON: { weights: {...}, expectedReturn, volatility, sharpeRatio }
async function loadOptimize() {
    loadingOptimize.value = true
    try {
        const data = await getOptimize()
        // 确保 data 是有效对象且包含 weights 字段
        if (data && typeof data === 'object' && data.weights) {
            optimize.value = {
                weights: data.weights,
                expectedReturn: Number(data.expectedReturn || 0),
                volatility: Number(data.volatility || 0),
                sharpeRatio: Number(data.sharpeRatio || 0)
            }
        } else {
            optimize.value = null
        }
    } catch {
        optimize.value = null
    } finally {
        loadingOptimize.value = false
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
    Promise.allSettled([loadPortfolio(), loadGoal(), loadHistory(), loadOptimize()])
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
/* 预警红点徽章：推至标题行右侧 */
.alert-badge { margin-left: auto; }
/* 盈亏趋势折线图卡 */
.trend-card { margin-bottom: 16px; }
.trend-wrap { height: 280px; position: relative; }
/* AI 资产配置建议卡 */
.optimize-card { margin-top: 16px; border-radius: 8px; }
.optimize-title { font-size: 14px; font-weight: 600; color: #202123; }
.optimize-content { display: flex; flex-direction: column; gap: 18px; }
.optimize-weights { display: flex; flex-direction: column; gap: 12px; }
.weight-row { display: flex; flex-direction: column; gap: 4px; }
.weight-label { font-size: 12px; color: #6e6f80; }
.optimize-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; padding-top: 4px; border-top: 1px solid #f7f7f8; }
.metric { text-align: center; }
.metric-value { font-size: 18px; font-weight: 700; color: #202123; }
.metric-label { font-size: 12px; color: #6e6f80; margin-top: 4px; }
@media (max-width: 768px) {
    .stats { grid-template-columns: repeat(2, 1fr); }
    .grid { grid-template-columns: 1fr; }
    .card.full { grid-column: auto; }
    .optimize-metrics { grid-template-columns: repeat(3, 1fr); }
}
</style>
