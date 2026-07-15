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

        <div class="analysis-view">
            <div class="content">
                <!-- 页面标题 -->
                <div class="header-row">
                    <h2 class="title">
                        <BarChartOutlined :style="{ fontSize: '22px' }" />
                        投资分析
                    </h2>
                </div>

                <!-- 组合优化权重 -->
                <div class="card">
                    <div class="card-title">组合优化权重</div>
                    <div class="optimize-grid">
                        <div class="chart-area">
                            <Doughnut
                                v-if="optimizeChartData"
                                :data="optimizeChartData"
                                :options="doughnutOptions"
                            />
                            <div v-else-if="optimizeLoading" class="chart-loading">
                                <a-spin />
                            </div>
                            <div v-else class="empty-state">
                                <p>暂无优化数据</p>
                            </div>
                        </div>
                        <div class="metrics">
                            <div class="metric-item">
                                <div class="metric-label">预期收益</div>
                                <div class="metric-value">{{ formatPercent(optimizeMetrics.expectedReturn) }}</div>
                            </div>
                            <div class="metric-item">
                                <div class="metric-label">波动率</div>
                                <div class="metric-value">{{ formatPercent(optimizeMetrics.volatility) }}</div>
                            </div>
                            <div class="metric-item">
                                <div class="metric-label">夏普比率</div>
                                <div class="metric-value" :style="{ color: '#1890ff' }">
                                    {{ formatNumber(optimizeMetrics.sharpeRatio) }}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 风险收益散点图 -->
                <div class="card">
                    <div class="card-title">风险收益散点图</div>
                    <p class="chart-hint">横轴：年化波动率，纵轴：年化收益率，气泡大小：市值</p>
                    <div class="scatter-wrap">
                        <Scatter
                            v-if="scatterChartData.datasets[0].data.length"
                            :data="scatterChartData"
                            :options="scatterOptions"
                        />
                        <div v-else-if="riskLoading" class="chart-loading">
                            <a-spin />
                        </div>
                        <div v-else class="empty-state">
                            <p>历史数据积累中，暂无法生成有效散点图</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { App } from 'ant-design-vue'
import { BarChartOutlined } from '@ant-design/icons-vue'
import { Doughnut, Scatter } from 'vue-chartjs'
import {
    Chart as ChartJS,
    ArcElement,
    PointElement,
    Tooltip,
    Legend,
    LinearScale,
    Title
} from 'chart.js'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { sessionsStore } from '../stores/sessions.js'
import { getOptimize, getRiskReturn } from '../api/analysis.js'

// 注册 Chart.js 组件
ChartJS.register(ArcElement, PointElement, Tooltip, Legend, LinearScale, Title)

const { state } = sessionsStore
const router = useRouter()
const { message } = App.useApp()

// 组合优化相关状态
const optimizeData = ref(null)
const optimizeLoading = ref(false)

// 风险收益相关状态
const riskReturnData = ref([])
const riskLoading = ref(false)

// 图表配色
const CHART_COLORS = [
    '#1890ff', '#52c41a', '#f5222d', '#faad14',
    '#722ed1', '#13c2c2', '#eb2f96', '#fa541c'
]

// ===== 组合优化 =====
const optimizeChartData = computed(() => {
    const d = optimizeData.value || {}
    // 兼容常见字段名
    const weights = d.weights || d.weight || d.allocation
    if (!weights) return null
    const entries = Object.entries(weights)
    if (!entries.length) return null
    return {
        labels: entries.map(([k]) => k),
        datasets: [{
            data: entries.map(([, v]) => Number(v)),
            backgroundColor: CHART_COLORS.slice(0, entries.length),
            borderWidth: 2,
            borderColor: '#ffffff'
        }]
    }
})

const optimizeMetrics = computed(() => {
    const d = optimizeData.value || {}
    return {
        expectedReturn: d.expectedReturn ?? d.expected_return,
        volatility: d.volatility ?? d.annualVolatility ?? d.annual_volatility,
        sharpeRatio: d.sharpeRatio ?? d.sharpe_ratio
    }
})

const doughnutOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
        legend: { position: 'right', labels: { font: { size: 12 } } },
        tooltip: {
            callbacks: {
                label: (ctx) => {
                    const val = Number(ctx.raw) || 0
                    return ` ${ctx.label}: ${(val * 100).toFixed(2)}%`
                }
            }
        }
    }
}

async function loadOptimize() {
    optimizeLoading.value = true
    try {
        const res = await getOptimize()
        // 后端返回结构化 JSON：{ weights: {...}, expectedReturn, volatility, sharpeRatio }
        // 兼容旧格式：若顶层直接含权重字段，则包装为 { weights: ... }
        if (res && typeof res === 'object') {
            if (res.weights && typeof res.weights === 'object') {
                optimizeData.value = res
            } else {
                // 兼容旧格式：顶层即为 weights 映射，或含 weight/allocation 字段
                const weights = res.weight || res.allocation || res
                if (weights && typeof weights === 'object' && Object.keys(weights).length) {
                    optimizeData.value = {
                        weights,
                        expectedReturn: res.expectedReturn ?? res.expected_return,
                        volatility: res.volatility ?? res.annualVolatility ?? res.annual_volatility,
                        sharpeRatio: res.sharpeRatio ?? res.sharpe_ratio
                    }
                } else {
                    optimizeData.value = null
                    message.warning('优化数据格式异常')
                }
            }
        } else {
            optimizeData.value = null
        }
    } catch (e) {
        message.error('获取优化数据失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
        optimizeData.value = null
    } finally {
        optimizeLoading.value = false
    }
}

// ===== 风险收益散点图 =====
const scatterChartData = computed(() => {
    const items = riskReturnData.value
    if (!items.length) return { datasets: [{ data: [] }] }
    const maxMv = Math.max(...items.map(i => Number(i.marketValue) || 0), 1)
    return {
        datasets: [{
            label: '资产',
            data: items.map(i => ({
                x: Number(i.annualVolatility) || 0,
                y: Number(i.annualReturn) || 0,
                r: scaleRadius(Number(i.marketValue) || 0, maxMv)
            })),
            backgroundColor: 'rgba(24, 144, 255, 0.6)',
            borderColor: '#1890ff'
        }]
    }
})

// 根据市值映射气泡半径（5~25px）
function scaleRadius(mv, maxMv) {
    if (maxMv <= 0) return 8
    return 5 + (mv / maxMv) * 20
}

const scatterOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
        legend: { display: false },
        tooltip: {
            callbacks: {
                label: (ctx) => {
                    const item = riskReturnData.value[ctx.dataIndex]
                    if (!item) return ''
                    return ` ${item.name}：收益 ${formatPercent(item.annualReturn)}，波动 ${formatPercent(item.annualVolatility)}，市值 ${formatMoney(item.marketValue)}`
                }
            }
        }
    },
    scales: {
        x: {
            title: { display: true, text: '年化波动率', font: { size: 12 } },
            ticks: { font: { size: 11 } }
        },
        y: {
            title: { display: true, text: '年化收益率', font: { size: 12 } },
            ticks: { font: { size: 11 } }
        }
    }
}

async function loadRiskReturn() {
    riskLoading.value = true
    try {
        riskReturnData.value = (await getRiskReturn()) || []
    } catch (e) {
        message.error('获取风险收益数据失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
        riskReturnData.value = []
    } finally {
        riskLoading.value = false
    }
}

// ===== 格式化工具 =====
function formatMoney(v) {
    if (v == null || v === '' || isNaN(v)) return '-'
    return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatNumber(v) {
    if (v == null || v === '' || isNaN(v)) return '-'
    return Number(v).toFixed(4)
}

function formatPercent(v) {
    if (v == null || v === '' || isNaN(v)) return '-'
    let p = Number(v)
    // 兼容 0-1 区间与百分数
    if (Math.abs(p) <= 1) p = p * 100
    return p.toFixed(2) + '%'
}

function goChat() {
    router.push('/chat')
}

onMounted(() => {
    loadOptimize()
    loadRiskReturn()
})
</script>

<style scoped>
.analysis-view { height: 100%; overflow-y: auto; background: #ffffff; }
.content { max-width: 1024px; margin: 0 auto; padding: 24px; }
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.title { margin: 0; display: flex; align-items: center; gap: 8px; font-size: 20px; color: #202123; font-weight: 600; }
.card { background: #ffffff; border: 1px solid #ececf1; border-radius: 8px; padding: 16px 20px; margin-bottom: 16px; }
.card-title { font-size: 15px; font-weight: 600; color: #202123; margin-bottom: 12px; }
.optimize-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; align-items: center; }
.chart-area { height: 280px; display: flex; align-items: center; justify-content: center; }
.chart-loading { padding: 40px; text-align: center; }
.metrics { display: flex; flex-direction: column; gap: 12px; }
.metric-item { padding: 16px; background: #f7f7f8; border-radius: 6px; text-align: center; }
.metric-label { font-size: 13px; color: #6e6f80; }
.metric-value { font-size: 22px; font-weight: 700; color: #202123; margin-top: 4px; }
.chart-hint { font-size: 12px; color: #6e6f80; margin: 0 0 12px 0; }
.scatter-wrap { height: 360px; }
.empty-state { padding: 40px 24px; text-align: center; color: #6e6f80; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.empty-state p { margin: 0; font-size: 14px; }
@media (max-width: 768px) {
    .optimize-grid { grid-template-columns: 1fr; }
}
</style>
