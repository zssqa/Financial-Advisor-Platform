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

        <div class="toolbox-view">
            <div class="content">
                <div class="header">
                    <h2 class="title">
                        <ToolOutlined style="font-size: 22px;" />
                        工具箱
                    </h2>
                </div>

                <a-tabs>
                    <!-- 基金筛选器 -->
                    <a-tab-pane key="fund" tab="基金筛选器">
                        <div class="tool-card">
                            <a-form
                                ref="fundFormRef"
                                :model="fundForm"
                                :rules="fundRules"
                                :label-col="{ style: { width: '120px' } }"
                            >
                                <a-form-item label="基金类型" name="fundType">
                                    <a-select
                                        v-model:value="fundForm.fundType"
                                        :options="fundTypeOptions"
                                        placeholder="请选择基金类型"
                                        allow-clear
                                    />
                                </a-form-item>

                                <a-form-item label="最低收益率" name="minReturn">
                                    <a-input-number
                                        v-model:value="fundForm.minReturn"
                                        :min="0"
                                        :precision="2"
                                        placeholder="近一年最低收益率"
                                        style="width: 100%"
                                    >
                                        <template #addonAfter>%</template>
                                    </a-input-number>
                                </a-form-item>

                                <a-form-item label="最大风险等级" name="maxRiskLevel">
                                    <a-select
                                        v-model:value="fundForm.maxRiskLevel"
                                        :options="riskLevelOptions"
                                        placeholder="可接受的最大风险等级"
                                    />
                                </a-form-item>

                                <div class="form-actions">
                                    <a-button type="primary" :loading="fundLoading" @click="handleFund">
                                        筛选
                                    </a-button>
                                </div>
                            </a-form>

                            <div v-if="fundLoaded" class="result-block">
                                <a-table
                                    :columns="fundColumns"
                                    :data-source="fundList"
                                    :loading="fundLoading"
                                    :bordered="false"
                                    :pagination="false"
                                />
                            </div>
                        </div>
                    </a-tab-pane>

                    <!-- 汇率换算 -->
                    <a-tab-pane key="exchange" tab="汇率换算">
                        <div class="tool-card">
                            <a-form
                                ref="exchangeFormRef"
                                :model="exchangeForm"
                                :rules="exchangeRules"
                                :label-col="{ style: { width: '120px' } }"
                            >
                                <a-form-item label="源货币" name="from">
                                    <a-select
                                        v-model:value="exchangeForm.from"
                                        :options="currencyOptions"
                                        placeholder="请选择源货币"
                                    />
                                </a-form-item>

                                <a-form-item label="目标货币" name="to">
                                    <a-select
                                        v-model:value="exchangeForm.to"
                                        :options="targetCurrencyOptions"
                                        placeholder="请选择目标货币"
                                    />
                                </a-form-item>

                                <a-form-item label="金额" name="amount">
                                    <a-input-number
                                        v-model:value="exchangeForm.amount"
                                        :min="0"
                                        :precision="2"
                                        placeholder="请输入换算金额"
                                        style="width: 100%"
                                    >
                                        <template #prefix>{{ exchangeForm.from }}</template>
                                    </a-input-number>
                                </a-form-item>

                                <div class="form-actions">
                                    <a-button type="primary" :loading="exchangeLoading" @click="handleExchange">
                                        换算
                                    </a-button>
                                </div>
                            </a-form>

                            <div v-if="exchangeResult" class="result-block exchange-result">
                                <a-statistic
                                    title="换算结果"
                                    :value="exchangeAmount"
                                    :formatter="() => formatMoney(exchangeAmount)"
                                    :value-style="{ fontVariantNumeric: 'tabular-nums' }"
                                >
                                    <template #prefix>
                                        <span class="currency-tag">{{ exchangeForm.to }}</span>
                                    </template>
                                </a-statistic>
                                <div v-if="exchangeRateValue != null" class="rate-hint">
                                    参考汇率：1 {{ exchangeForm.from }} ≈ {{ exchangeRateValue }} {{ exchangeForm.to }}
                                </div>
                            </div>
                        </div>
                    </a-tab-pane>
                </a-tabs>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { App } from 'ant-design-vue'
import { ToolOutlined } from '@ant-design/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { sessionsStore } from '../stores/sessions.js'
import {
    screenFunds, exchangeRate
} from '../api/tool.js'

const { state } = sessionsStore
const router = useRouter()
const { message } = App.useApp()

/* ============== 通用工具函数 ============== */

// 金额格式化（带 ¥ 前缀，保留两位小数）
function formatMoney(v) {
    if (v == null || v === '') return '-'
    const n = Number(v)
    if (isNaN(n)) return '-'
    return '¥' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 百分比格式化（兼容 0-1 小数与已为百分比的数值）
function formatPercent(v) {
    if (v == null || v === '' || isNaN(v)) return '-'
    let p = Number(v)
    if (p <= 1 && p > -1) p = p * 100
    return p.toFixed(2) + '%'
}

// 解析后端响应：兼容 { data: ... } 包装或直接返回业务数据
function unwrap(res) {
    const body = res?.data
    if (body && typeof body === 'object' && 'data' in body) return body.data
    return body
}

// 从响应中提取列表数据
function extractList(res) {
    const d = unwrap(res)
    if (Array.isArray(d)) return d
    if (Array.isArray(d?.funds)) return d.funds
    if (Array.isArray(d?.list)) return d.list
    if (Array.isArray(d?.data)) return d.data
    return []
}

// 正数校验规则（金额必须为正数）
function positiveRule(label) {
    return {
        type: 'number',
        required: true,
        validator: (rule, value) => {
            if (value == null) throw new Error(`请输入${label}`)
            if (Number(value) <= 0) throw new Error(`${label}必须为正数`)
            return true
        },
        trigger: ['blur', 'change']
    }
}

// 非负校验规则（可选扣除项，允许为 0）
function nonNegativeRule(label) {
    return {
        type: 'number',
        required: false,
        validator: (rule, value) => {
            if (value == null) return true
            if (Number(value) < 0) throw new Error(`${label}不能为负数`)
            return true
        },
        trigger: ['blur', 'change']
    }
}

/* ============== 基金筛选器 ============== */

const fundFormRef = ref(null)
const fundLoading = ref(false)
const fundLoaded = ref(false)
const fundList = ref([])

const fundForm = reactive({
    fundType: null,
    minReturn: null,
    maxRiskLevel: null
})

const fundTypeOptions = [
    { label: '股票型', value: 'equity' },
    { label: '混合型', value: 'hybrid' },
    { label: '债券型', value: 'bond' },
    { label: '指数型', value: 'index' }
]

const riskLevelOptions = [
    { label: '1 级（低风险）', value: 1 },
    { label: '2 级（中低风险）', value: 2 },
    { label: '3 级（中风险）', value: 3 },
    { label: '4 级（中高风险）', value: 4 },
    { label: '5 级（高风险）', value: 5 }
]

const fundRules = {
    minReturn: nonNegativeRule('最低收益率'),
    maxRiskLevel: {
        type: 'number',
        required: true,
        message: '请选择最大风险等级',
        trigger: ['change', 'blur']
    }
}

// 基金类型中文映射
const FUND_TYPE_LABELS = {
    equity: '股票型', hybrid: '混合型', bond: '债券型', index: '指数型'
}

const fundColumns = [
    { title: '基金名称', dataIndex: 'name', key: 'name', customRender: ({ record }) => record.name || record.fundName || '-' },
    { title: '基金代码', dataIndex: 'code', key: 'code', customRender: ({ record }) => record.code || record.fundCode || '-' },
    {
        title: '类型', dataIndex: 'type', key: 'type',
        customRender: ({ record }) => record.type || FUND_TYPE_LABELS[record.typeCode] || record.typeName || '-'
    },
    {
        title: '近一年收益', dataIndex: 'returnRate', key: 'returnRate',
        customRender: ({ record }) => formatPercent(record.returnRate ?? record.yearlyReturn)
    },
    {
        title: '风险等级', dataIndex: 'riskLevel', key: 'riskLevel',
        customRender: ({ record }) => record.riskLevel || '-'
    }
]

async function handleFund() {
    try {
        await fundFormRef.value?.validate()
    } catch {
        return
    }
    fundLoading.value = true
    try {
        const res = await screenFunds({
            fundType: fundForm.fundType,
            minReturn: Number(fundForm.minReturn) || null,
            maxRisk: fundForm.maxRiskLevel
        })
        fundList.value = extractList(res)
        fundLoaded.value = true
        message.success(`筛选完成，共 ${fundList.value.length} 只基金`)
    } catch (e) {
        message.error('筛选失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    } finally {
        fundLoading.value = false
    }
}

/* ============== 汇率换算 ============== */

const exchangeFormRef = ref(null)
const exchangeLoading = ref(false)
const exchangeResult = ref(null)

const exchangeForm = reactive({
    from: 'USD',
    to: 'CNY',
    amount: null
})

const currencyOptions = [
    { label: '美元 (USD)', value: 'USD' },
    { label: '欧元 (EUR)', value: 'EUR' },
    { label: '日元 (JPY)', value: 'JPY' },
    { label: '港元 (HKD)', value: 'HKD' },
    { label: '英镑 (GBP)', value: 'GBP' }
]

const targetCurrencyOptions = [
    { label: '人民币 (CNY)', value: 'CNY' }
]

const exchangeRules = {
    from: { required: true, message: '请选择源货币', trigger: ['change', 'blur'] },
    to: { required: true, message: '请选择目标货币', trigger: ['change', 'blur'] },
    amount: positiveRule('换算金额')
}

// 兼容后端不同字段名提取换算后金额
const exchangeAmount = computed(() => {
    const r = exchangeResult.value
    if (!r) return null
    return r.convertedAmount ?? r.result ?? r.amount ?? r.value ?? r.targetAmount ?? null
})

// 兼容提取参考汇率
const exchangeRateValue = computed(() => {
    const r = exchangeResult.value
    if (!r) return null
    return r.rate ?? r.exchangeRate ?? r.exchange_rate ?? null
})

async function handleExchange() {
    try {
        await exchangeFormRef.value?.validate()
    } catch {
        return
    }
    exchangeLoading.value = true
    try {
        const res = await exchangeRate(exchangeForm.from, exchangeForm.to, exchangeForm.amount)
        exchangeResult.value = unwrap(res) || {}
        message.success('换算完成')
    } catch (e) {
        message.error('换算失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    } finally {
        exchangeLoading.value = false
    }
}

function goChat() {
    router.push('/chat')
}
</script>

<style scoped>
.toolbox-view { height: 100%; overflow-y: auto; background: #ffffff; }
.content { max-width: 1024px; margin: 0 auto; padding: 24px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.title {
    margin: 0;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 20px;
    color: #202123;
    font-weight: 600;
}
.tool-card {
    background: #ffffff;
    border: 1px solid #ececf1;
    border-radius: 10px;
    padding: 20px 24px;
    margin-top: 8px;
}
.form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 8px;
}
.result-block {
    margin-top: 24px;
    padding-top: 20px;
    border-top: 1px solid #f0f0f5;
}
.highlight { color: #10a37f; font-weight: 700; }
.stat-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
}
.exchange-result {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
}
.currency-tag {
    display: inline-block;
    padding: 2px 8px;
    background: #e6f7ff;
    color: #1890ff;
    border-radius: 4px;
    font-size: 16px;
    margin-right: 6px;
}
.rate-hint {
    font-size: 16px;
    color: #6e6f80;
}

/* 字体规范：正文 16px+，输入框 18px+ */
:deep(.ant-form-item-label > label) { font-size: 16px; }
:deep(.ant-input),
:deep(.ant-input-number-input) { font-size: 18px; }
:deep(.ant-select-selection-item),
:deep(.ant-select-selection-placeholder) { font-size: 18px; }
:deep(.ant-descriptions-item-content) { font-size: 16px; }
:deep(.ant-statistic-content-value) { font-size: 22px; }
:deep(.ant-statistic .ant-statistic-title) { font-size: 16px; }
:deep(.ant-table-thead .ant-table-cell) { font-size: 16px; }
:deep(.ant-table-tbody .ant-table-cell) { font-size: 16px; }
:deep(.ant-tabs-tab-btn) { font-size: 16px; }

@media (max-width: 768px) {
    .content { padding: 16px; }
    .stat-grid { grid-template-columns: 1fr; }
}
</style>
