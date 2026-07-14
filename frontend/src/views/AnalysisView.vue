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
                        <n-icon size="22"><BarChartOutline /></n-icon>
                        投资分析
                    </h2>
                </div>

                <!-- K线图区 -->
                <div class="card">
                    <div class="card-title">K线图</div>
                    <div class="search-row">
                        <n-input
                            v-model:value="symbol"
                            placeholder="输入股票代码，如 sh600036"
                            clearable
                            @keyup.enter="loadKline"
                        />
                        <n-button type="primary" :loading="klineLoading" @click="loadKline">
                            查询
                        </n-button>
                    </div>
                    <div class="quick-stocks">
                        <span class="quick-label">快捷选择：</span>
                        <n-tag
                            v-for="s in quickStocks"
                            :key="s.code"
                            :type="symbol === s.code ? 'primary' : 'default'"
                            size="small"
                            checkable
                            :checked="symbol === s.code"
                            @click="selectStock(s.code)"
                        >
                            {{ s.name }}
                        </n-tag>
                    </div>
                    <div v-if="klineLoading" class="kline-loading">
                        <n-spin size="medium" />
                        <span>正在生成K线图...</span>
                    </div>
                    <div v-else-if="klineUrl" class="kline-img">
                        <img :src="klineUrl" alt="K线图" />
                    </div>
                    <div v-else class="empty-state">
                        <n-icon size="40" color="#d9d9e3"><BarChartOutline /></n-icon>
                        <p>输入股票代码或选择快捷股票，查看K线图</p>
                    </div>
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
                                <n-spin size="medium" />
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
                            <n-spin size="medium" />
                        </div>
                        <div v-else class="empty-state">
                            <p>暂无风险收益数据</p>
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
import { NIcon, NButton, NInput, NTag, NSpin, useMessage } from 'naive-ui'
import { BarChartOutline } from '@vicons/ionicons5'
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
import { getKline, getOptimize, getRiskReturn } from '../api/analysis.js'

// 注册 Chart.js 组件
ChartJS.register(ArcElement, PointElement, Tooltip, Legend, LinearScale, Title)

const { state } = sessionsStore
const router = useRouter()
const message = useMessage()

// K线图相关状态
const symbol = ref('sh600036')
const klineRaw = ref('')
const klineLoading = ref(false)

// 组合优化相关状态
const optimizeData = ref(null)
const optimizeLoading = ref(false)

// 风险收益相关状态
const riskReturnData = ref([])
const riskLoading = ref(false)

// 常见股票快捷选项
const quickStocks = [
    { name: '招商银行', code: 'sh600036' },
    { name: '平安银行', code: 'sz000001' },
    { name: '贵州茅台', code: 'sh600519' }
]

// 图表配色
const CHART_COLORS = [
    '#1890ff', '#52c41a', '#f5222d', '#faad14',
    '#722ed1', '#13c2c2', '#eb2f96', '#fa541c'
]

// ===== K线图 =====
// 处理后端返回的图片路径或 base64，兼容多种返回格式
const klineUrl = computed(() => {
    const raw = klineRaw.value
    if (!raw) return ''
    if (typeof raw === 'string') {
        // base64 或完整 URL 直接使用
        if (raw.startsWith('data:') || raw.startsWith('http')) return raw
        // 以 / 开头的服务器路径直接使用
        if (raw.startsWith('/')) return raw
        // 相对路径补充 /api 前缀
        return '/api/' + raw
    }
    // 对象形式，尝试常见字段
    return raw.url || raw.path || raw.image || ''
})

function selectStock(code) {
    symbol.value = code
    loadKline()
}

async function loadKline() {
    const sym = symbol.value?.trim()
    if (!sym) {
        message.warning('请输入股票代码')
        return
    }
    klineLoading.value = true
    try {
        klineRaw.value = await getKline(sym)
        if (!klineUrl.value) {
            message.warning('未获取到K线图数据')
        }
    } catch (e) {
        message.error('获取K线图失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
        klineRaw.value = ''
    } finally {
        klineLoading.value = false
    }
}

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
        optimizeData.value = await getOptimize()
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
    loadKline()
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
.search-row { display: flex; gap: 8px; margin-bottom: 12px; }
.quick-stocks { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
.quick-label { font-size: 13px; color: #6e6f80; }
.kline-loading { padding: 40px; text-align: center; color: #6e6f80; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.kline-img { text-align: center; }
.kline-img img { max-width: 100%; border-radius: 4px; }
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
    .search-row { flex-direction: column; }
}
</style>
