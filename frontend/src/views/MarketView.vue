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

        <div class="market-view">
            <div class="content">
                <h2 class="title">
                    <n-icon size="22"><TrendingUpOutline /></n-icon>
                    市场行情
                </h2>

                <div class="blocks">
                    <!-- 四大指数 -->
                    <n-card class="block" :bordered="true">
                        <template #header>
                            <div class="block-title">
                                <n-icon size="18" color="#202123"><StatsChartOutline /></n-icon>
                                四大指数
                            </div>
                        </template>
                        <n-spin :show="loading.indices">
                            <div class="spin-area">
                                <n-grid
                                    v-if="indices.length"
                                    cols="1 s:2 m:4"
                                    responsive="screen"
                                    :x-gap="14"
                                    :y-gap="14"
                                >
                                    <n-grid-item v-for="(item, idx) in indices" :key="idx">
                                        <n-card class="index-card" size="small" :bordered="true">
                                            <div class="index-name">{{ indexName(item) }}</div>
                                            <div
                                                class="index-price"
                                                :style="{ color: changeColor(indexChange(item)) }"
                                            >
                                                {{ formatPoint(indexPrice(item)) }}
                                            </div>
                                            <div
                                                class="index-change"
                                                :style="{ color: changeColor(indexChange(item)) }"
                                            >
                                                <span class="arrow">
                                                    {{ indexChange(item) >= 0 ? '▲' : '▼' }}
                                                </span>
                                                {{ formatPercent(indexChange(item)) }}
                                            </div>
                                        </n-card>
                                    </n-grid-item>
                                </n-grid>
                                <n-empty
                                    v-else-if="!loading.indices"
                                    :description="errors.indices ? '指数数据加载失败' : '暂无指数数据'"
                                >
                                    <template #extra>
                                        <n-button
                                            v-if="errors.indices"
                                            size="small"
                                            type="primary"
                                            @click="loadIndices"
                                        >
                                            重试
                                        </n-button>
                                    </template>
                                </n-empty>
                            </div>
                        </n-spin>
                    </n-card>

                    <!-- 市场情绪 + 金融日历 -->
                    <n-grid cols="1 m:2" responsive="screen" :x-gap="16" :y-gap="16">
                        <!-- 市场情绪 -->
                        <n-grid-item>
                            <n-card class="block" :bordered="true">
                                <template #header>
                                    <div class="block-title">
                                        <n-icon size="18" color="#202123"><PulseOutline /></n-icon>
                                        市场情绪
                                    </div>
                                </template>
                                <n-spin :show="loading.sentiment">
                                    <div class="spin-area">
                                        <div v-if="sentiment" class="gauge-wrap">
                                            <div class="gauge">
                                                <n-progress
                                                    type="circle"
                                                    :percentage="sentimentValue(sentiment)"
                                                    :color="sentimentInfo.color"
                                                    rail-color="#ececf1"
                                                    :stroke-width="10"
                                                    :show-indicator="false"
                                                />
                                                <div class="gauge-center">
                                                    <div
                                                        class="gauge-value"
                                                        :style="{ color: sentimentInfo.color }"
                                                    >
                                                        {{ sentimentValue(sentiment) }}
                                                    </div>
                                                    <div class="gauge-label">{{ sentimentInfo.label }}</div>
                                                </div>
                                            </div>
                                            <div class="gauge-desc">
                                                <n-tag
                                                    size="small"
                                                    round
                                                    :bordered="false"
                                                    :color="{ color: sentimentInfo.color, textColor: '#ffffff' }"
                                                >
                                                    {{ sentimentInfo.label }}
                                                </n-tag>
                                                <p class="gauge-text">{{ sentimentInfo.desc }}</p>
                                                <p class="gauge-hint">贪婪与恐惧指数（0 = 极度恐惧，100 = 极度贪婪）</p>
                                            </div>
                                        </div>
                                        <n-empty
                                            v-else-if="!loading.sentiment"
                                            :description="errors.sentiment ? '情绪数据加载失败' : '暂无情绪数据'"
                                        >
                                            <template #extra>
                                                <n-button
                                                    v-if="errors.sentiment"
                                                    size="small"
                                                    type="primary"
                                                    @click="loadSentiment"
                                                >
                                                    重试
                                                </n-button>
                                            </template>
                                        </n-empty>
                                    </div>
                                </n-spin>
                            </n-card>
                        </n-grid-item>

                        <!-- 金融日历 -->
                        <n-grid-item>
                            <n-card class="block" :bordered="true">
                                <template #header>
                                    <div class="block-title">
                                        <n-icon size="18" color="#202123"><CalendarNumberOutline /></n-icon>
                                        本周金融日历
                                    </div>
                                </template>
                                <n-spin :show="loading.calendar">
                                    <div class="spin-area">
                                        <n-timeline v-if="calendar.length" size="large">
                                            <n-timeline-item
                                                v-for="(item, idx) in calendar"
                                                :key="idx"
                                                :type="timelineType(item)"
                                                :time="calTime(item)"
                                            >
                                                <template #header>
                                                    <span class="cal-title">{{ calTitle(item) }}</span>
                                                </template>
                                                <div class="cal-body">
                                                    <div class="cal-tags">
                                                        <n-tag
                                                            v-if="calCountry(item)"
                                                            size="small"
                                                            round
                                                            :bordered="false"
                                                            type="info"
                                                        >
                                                            {{ calCountry(item) }}
                                                        </n-tag>
                                                        <n-tag
                                                            size="small"
                                                            round
                                                            :bordered="false"
                                                            :type="importanceTagType(item)"
                                                        >
                                                            {{ importanceLabel(item) }}
                                                        </n-tag>
                                                    </div>
                                                    <div class="cal-figures">
                                                        <span>预期 {{ calFig(item, 'forecast') }}</span>
                                                        <span>前值 {{ calFig(item, 'previous') }}</span>
                                                        <span v-if="hasFig(item, 'actual')" class="cal-actual">
                                                            公布 {{ calFig(item, 'actual') }}
                                                        </span>
                                                    </div>
                                                </div>
                                            </n-timeline-item>
                                        </n-timeline>
                                        <n-empty
                                            v-else-if="!loading.calendar"
                                            :description="errors.calendar ? '日历数据加载失败' : '本周暂无重要数据发布'"
                                        >
                                            <template #extra>
                                                <n-button
                                                    v-if="errors.calendar"
                                                    size="small"
                                                    type="primary"
                                                    @click="loadCalendar"
                                                >
                                                    重试
                                                </n-button>
                                            </template>
                                        </n-empty>
                                    </div>
                                </n-spin>
                            </n-card>
                        </n-grid-item>
                    </n-grid>
                </div>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
    NIcon, NSpin, NEmpty, NButton, NProgress, NTag, NCard,
    NGrid, NGridItem, NTimeline, NTimelineItem
} from 'naive-ui'
import {
    TrendingUpOutline, StatsChartOutline, PulseOutline, CalendarNumberOutline
} from '@vicons/ionicons5'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { sessionsStore } from '../stores/sessions.js'
import { getMarketIndices, getMarketSentiment, getMarketCalendar } from '../api/market.js'

const { state } = sessionsStore
const router = useRouter()

// 各模块数据
const indices = ref([])
const sentiment = ref(null)
const calendar = ref([])

// 加载与错误状态
const loading = reactive({ indices: true, sentiment: true, calendar: true })
const errors = reactive({ indices: false, sentiment: false, calendar: false })

// ===== 四大指数字段适配（兼容后端不同命名） =====
function indexName(i) {
    return i.name || i.indexName || i.symbol || i.code || '—'
}
function indexPrice(i) {
    return Number(i.price ?? i.point ?? i.value ?? i.close ?? i.last ?? 0)
}
function indexChange(i) {
    return Number(i.changePercent ?? i.pctChange ?? i.percent ?? i.changePct ?? i.change ?? 0)
}
// A股习惯：涨红跌绿
function changeColor(v) {
    return Number(v) >= 0 ? '#e74c3c' : '#2ecc71'
}
function formatPoint(v) {
    return Number(v || 0).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    })
}
function formatPercent(v) {
    const n = Number(v || 0)
    return (n > 0 ? '+' : '') + n.toFixed(2) + '%'
}

// ===== 市场情绪（贪婪/恐惧指数 0-100） =====
function sentimentValue(s) {
    const v = Number(s?.value ?? s?.score ?? s?.index ?? s?.greedIndex ?? 50)
    if (isNaN(v)) return 50
    return Math.max(0, Math.min(100, v))
}
const sentimentInfo = computed(() => {
    const s = sentiment.value
    if (!s) return { label: '—', color: '#a0a0b0', desc: '' }
    const v = sentimentValue(s)
    if (v <= 24) return { label: '极度恐惧', color: '#2ecc71', desc: '市场极度悲观，或为逆向布局机会' }
    if (v <= 44) return { label: '恐惧', color: '#58d68d', desc: '市场情绪偏弱，投资者较为谨慎' }
    if (v <= 55) return { label: '中性', color: '#f4d03f', desc: '多空力量均衡，方向尚不明朗' }
    if (v <= 75) return { label: '贪婪', color: '#f39c12', desc: '市场情绪高涨，注意追高风险' }
    return { label: '极度贪婪', color: '#e74c3c', desc: '市场过热，需警惕回调风险' }
})

// ===== 金融日历字段适配 =====
function calTime(c) {
    const t = c.date || c.time || c.publishTime || c.datetime || c.publishedAt
    if (!t) return ''
    return formatDate(t)
}
function calTitle(c) {
    return c.event || c.title || c.name || c.indicator || '经济数据'
}
function calCountry(c) {
    return c.country || c.region || c.area || ''
}
function calImportance(c) {
    const imp = String(c.importance ?? c.priority ?? '').toLowerCase()
    if (['high', '高', '3', '***', '★★★'].includes(imp)) return 'high'
    if (['low', '低', '1', '*', '★'].includes(imp)) return 'low'
    return 'medium'
}
function importanceLabel(c) {
    const m = calImportance(c)
    return m === 'high' ? '高影响' : m === 'low' ? '低影响' : '中影响'
}
function importanceTagType(c) {
    const m = calImportance(c)
    return m === 'high' ? 'error' : m === 'low' ? 'default' : 'warning'
}
function timelineType(c) {
    const m = calImportance(c)
    return m === 'high' ? 'error' : m === 'low' ? 'default' : 'warning'
}
// 经济数据指标值（预期/前值/公布）的多种字段名兼容
const FIG_ALIASES = {
    forecast: ['forecast', 'expected', 'consensus', '预期'],
    previous: ['previous', 'prev', 'last', '前值'],
    actual: ['actual', 'real', 'result', '公布']
}
function calFig(c, key) {
    for (const a of FIG_ALIASES[key]) {
        if (c[a] !== undefined && c[a] !== null && c[a] !== '') return c[a]
    }
    return '—'
}
function hasFig(c, key) {
    for (const a of FIG_ALIASES[key]) {
        if (c[a] !== undefined && c[a] !== null && c[a] !== '') return true
    }
    return false
}
function formatDate(t) {
    const d = new Date(t)
    if (isNaN(d.getTime())) return String(t)
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mi = String(d.getMinutes()).padStart(2, '0')
    return `${mm}-${dd} ${hh}:${mi}`
}

// ===== 数据加载 =====
async function loadIndices() {
    loading.indices = true
    errors.indices = false
    try {
        const data = await getMarketIndices()
        indices.value = Array.isArray(data) ? data : (Array.isArray(data?.list) ? data.list : [])
    } catch {
        indices.value = []
        errors.indices = true
    } finally {
        loading.indices = false
    }
}
async function loadSentiment() {
    loading.sentiment = true
    errors.sentiment = false
    try {
        const data = await getMarketSentiment()
        sentiment.value = data || null
    } catch {
        sentiment.value = null
        errors.sentiment = true
    } finally {
        loading.sentiment = false
    }
}
async function loadCalendar() {
    loading.calendar = true
    errors.calendar = false
    try {
        const data = await getMarketCalendar()
        calendar.value = Array.isArray(data) ? data : (Array.isArray(data?.list) ? data.list : [])
    } catch {
        calendar.value = []
        errors.calendar = true
    } finally {
        loading.calendar = false
    }
}

function goChat() {
    router.push('/chat')
}

onMounted(() => {
    Promise.allSettled([loadIndices(), loadSentiment(), loadCalendar()])
})
</script>

<style scoped>
.market-view {
    height: 100%;
    overflow-y: auto;
    background: #ffffff;
}
.content {
    max-width: 1024px;
    margin: 0 auto;
    padding: 24px;
}
.title {
    margin: 0 0 24px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 20px;
    color: #202123;
    font-weight: 600;
}
.blocks {
    display: flex;
    flex-direction: column;
    gap: 16px;
}
.block-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 18px;
    font-weight: 600;
    color: #202123;
}
.spin-area {
    min-height: 160px;
}
.index-card {
    border-radius: 8px;
}
.index-name {
    font-size: 16px;
    color: #6e6f80;
    margin-bottom: 8px;
}
.index-price {
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;
}
.index-change {
    font-size: 16px;
    font-weight: 600;
    margin-top: 6px;
    display: flex;
    align-items: center;
    gap: 4px;
}
.index-change .arrow {
    font-size: 14px;
}
.gauge-wrap {
    display: flex;
    align-items: center;
    gap: 24px;
    flex-wrap: wrap;
}
.gauge {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}
.gauge-center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    pointer-events: none;
}
.gauge-value {
    font-size: 36px;
    font-weight: 700;
    line-height: 1;
}
.gauge-label {
    font-size: 16px;
    color: #6e6f80;
    margin-top: 4px;
}
.gauge-desc {
    flex: 1;
    min-width: 180px;
}
.gauge-text {
    font-size: 16px;
    color: #202123;
    margin: 10px 0 6px;
    line-height: 1.5;
}
.gauge-hint {
    font-size: 14px;
    color: #a0a0b0;
    margin: 0;
    line-height: 1.5;
}
.cal-title {
    font-size: 16px;
    font-weight: 600;
    color: #202123;
}
.cal-body {
    margin-top: 8px;
    display: flex;
    flex-direction: column;
    gap: 8px;
}
.cal-tags {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}
.cal-figures {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;
    font-size: 14px;
    color: #6e6f80;
}
.cal-actual {
    color: #1890ff;
    font-weight: 600;
}
@media (max-width: 768px) {
    .content {
        padding: 16px;
    }
    .gauge-wrap {
        flex-direction: column;
        align-items: flex-start;
    }
}
</style>