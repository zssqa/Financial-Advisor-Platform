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
                        <n-icon size="22"><BuildOutline /></n-icon>
                        工具箱
                    </h2>
                </div>

                <n-tabs type="line" animated>
                    <!-- 个税计算器 -->
                    <n-tab-pane name="tax" tab="个税计算器">
                        <div class="tool-card">
                            <n-form
                                ref="taxFormRef"
                                :model="taxForm"
                                :rules="taxRules"
                                label-placement="left"
                                label-width="120"
                                require-mark-placement="right-hanging"
                            >
                                <n-form-item label="年薪收入" path="annualIncome">
                                    <n-input-number
                                        v-model:value="taxForm.annualIncome"
                                        :min="0"
                                        :precision="2"
                                        placeholder="请输入年薪收入"
                                        style="width: 100%"
                                        clearable
                                    >
                                        <template #prefix>¥</template>
                                    </n-input-number>
                                </n-form-item>

                                <n-form-item label="社保公积金扣除" path="socialInsurance">
                                    <n-input-number
                                        v-model:value="taxForm.socialInsurance"
                                        :min="0"
                                        :precision="2"
                                        placeholder="社保及公积金扣除合计"
                                        style="width: 100%"
                                        clearable
                                    >
                                        <template #prefix>¥</template>
                                    </n-input-number>
                                </n-form-item>

                                <n-form-item label="专项附加扣除" path="specialDeduction">
                                    <n-input-number
                                        v-model:value="taxForm.specialDeduction"
                                        :min="0"
                                        :precision="2"
                                        placeholder="专项附加扣除合计"
                                        style="width: 100%"
                                        clearable
                                    >
                                        <template #prefix>¥</template>
                                    </n-input-number>
                                </n-form-item>

                                <div class="form-actions">
                                    <n-button type="primary" :loading="taxLoading" @click="handleTax">
                                        计算
                                    </n-button>
                                </div>
                            </n-form>

                            <div v-if="taxResult" class="result-block">
                                <n-descriptions label-placement="left" bordered :column="1" size="large">
                                    <n-descriptions-item label="应纳税所得额">
                                        {{ formatMoney(taxResult.taxableIncome) }}
                                    </n-descriptions-item>
                                    <n-descriptions-item label="适用税率">
                                        {{ formatPercent(taxResult.taxRate) }}
                                    </n-descriptions-item>
                                    <n-descriptions-item label="速算扣除数">
                                        {{ formatMoney(taxResult.quickDeduction) }}
                                    </n-descriptions-item>
                                    <n-descriptions-item label="应纳税额">
                                        {{ formatMoney(taxResult.taxAmount) }}
                                    </n-descriptions-item>
                                    <n-descriptions-item label="税后收入">
                                        <span class="highlight">{{ formatMoney(taxResult.afterTaxIncome) }}</span>
                                    </n-descriptions-item>
                                </n-descriptions>
                            </div>
                        </div>
                    </n-tab-pane>

                    <!-- 基金筛选器 -->
                    <n-tab-pane name="fund" tab="基金筛选器">
                        <div class="tool-card">
                            <n-form
                                ref="fundFormRef"
                                :model="fundForm"
                                :rules="fundRules"
                                label-placement="left"
                                label-width="120"
                                require-mark-placement="right-hanging"
                            >
                                <n-form-item label="基金类型" path="fundType">
                                    <n-select
                                        v-model:value="fundForm.fundType"
                                        :options="fundTypeOptions"
                                        placeholder="请选择基金类型"
                                        clearable
                                    />
                                </n-form-item>

                                <n-form-item label="最低收益率" path="minReturn">
                                    <n-input-number
                                        v-model:value="fundForm.minReturn"
                                        :min="0"
                                        :precision="2"
                                        placeholder="近一年最低收益率"
                                        style="width: 100%"
                                        clearable
                                    >
                                        <template #suffix>%</template>
                                    </n-input-number>
                                </n-form-item>

                                <n-form-item label="最大风险等级" path="maxRiskLevel">
                                    <n-select
                                        v-model:value="fundForm.maxRiskLevel"
                                        :options="riskLevelOptions"
                                        placeholder="可接受的最大风险等级"
                                    />
                                </n-form-item>

                                <div class="form-actions">
                                    <n-button type="primary" :loading="fundLoading" @click="handleFund">
                                        筛选
                                    </n-button>
                                </div>
                            </n-form>

                            <div v-if="fundLoaded" class="result-block">
                                <n-data-table
                                    :columns="fundColumns"
                                    :data="fundList"
                                    :loading="fundLoading"
                                    :bordered="false"
                                    :single-line="false"
                                />
                            </div>
                        </div>
                    </n-tab-pane>

                    <!-- 汇率换算 -->
                    <n-tab-pane name="exchange" tab="汇率换算">
                        <div class="tool-card">
                            <n-form
                                ref="exchangeFormRef"
                                :model="exchangeForm"
                                :rules="exchangeRules"
                                label-placement="left"
                                label-width="120"
                                require-mark-placement="right-hanging"
                            >
                                <n-form-item label="源货币" path="from">
                                    <n-select
                                        v-model:value="exchangeForm.from"
                                        :options="currencyOptions"
                                        placeholder="请选择源货币"
                                    />
                                </n-form-item>

                                <n-form-item label="目标货币" path="to">
                                    <n-select
                                        v-model:value="exchangeForm.to"
                                        :options="targetCurrencyOptions"
                                        placeholder="请选择目标货币"
                                    />
                                </n-form-item>

                                <n-form-item label="金额" path="amount">
                                    <n-input-number
                                        v-model:value="exchangeForm.amount"
                                        :min="0"
                                        :precision="2"
                                        placeholder="请输入换算金额"
                                        style="width: 100%"
                                        clearable
                                    >
                                        <template #prefix>{{ exchangeForm.from }}</template>
                                    </n-input-number>
                                </n-form-item>

                                <div class="form-actions">
                                    <n-button type="primary" :loading="exchangeLoading" @click="handleExchange">
                                        换算
                                    </n-button>
                                </div>
                            </n-form>

                            <div v-if="exchangeResult" class="result-block exchange-result">
                                <n-statistic label="换算结果" tabular-nums>
                                    <template #prefix>
                                        <span class="currency-tag">{{ exchangeForm.to }}</span>
                                    </template>
                                    <template #default>
                                        {{ formatMoney(exchangeAmount) }}
                                    </template>
                                </n-statistic>
                                <div v-if="exchangeRateValue != null" class="rate-hint">
                                    参考汇率：1 {{ exchangeForm.from }} ≈ {{ exchangeRateValue }} {{ exchangeForm.to }}
                                </div>
                            </div>
                        </div>
                    </n-tab-pane>

                    <!-- 信用卡分期计算器 -->
                    <n-tab-pane name="installment" tab="信用卡分期计算器">
                        <div class="tool-card">
                            <n-form
                                ref="installmentFormRef"
                                :model="installmentForm"
                                :rules="installmentRules"
                                label-placement="left"
                                label-width="120"
                                require-mark-placement="right-hanging"
                            >
                                <n-form-item label="分期总额" path="totalAmount">
                                    <n-input-number
                                        v-model:value="installmentForm.totalAmount"
                                        :min="0"
                                        :precision="2"
                                        placeholder="请输入分期总额"
                                        style="width: 100%"
                                        clearable
                                    >
                                        <template #prefix>¥</template>
                                    </n-input-number>
                                </n-form-item>

                                <n-form-item label="分期期数" path="periods">
                                    <n-select
                                        v-model:value="installmentForm.periods"
                                        :options="periodOptions"
                                        placeholder="请选择分期期数"
                                    />
                                </n-form-item>

                                <n-form-item label="年化费率" path="annualRate">
                                    <n-input-number
                                        v-model:value="installmentForm.annualRate"
                                        :min="0"
                                        :precision="4"
                                        placeholder="请输入年化费率"
                                        style="width: 100%"
                                        clearable
                                    >
                                        <template #suffix>%</template>
                                    </n-input-number>
                                </n-form-item>

                                <div class="form-actions">
                                    <n-button type="primary" :loading="installmentLoading" @click="handleInstallment">
                                        计算
                                    </n-button>
                                </div>
                            </n-form>

                            <div v-if="installmentResult" class="result-block stat-grid">
                                <n-statistic label="每期还款额" tabular-nums>
                                    <template #default>{{ formatMoney(installmentResult.periodPayment) }}</template>
                                </n-statistic>
                                <n-statistic label="总利息" tabular-nums>
                                    <template #default>{{ formatMoney(installmentResult.totalInterest) }}</template>
                                </n-statistic>
                                <n-statistic label="总还款额" tabular-nums>
                                    <template #default>
                                        <span class="highlight">{{ formatMoney(installmentResult.totalRepayment) }}</span>
                                    </template>
                                </n-statistic>
                            </div>
                        </div>
                    </n-tab-pane>
                </n-tabs>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
    NIcon, NTabs, NTabPane, NForm, NFormItem, NInputNumber, NSelect,
    NButton, NStatistic, NDataTable, NDescriptions, NDescriptionsItem, useMessage
} from 'naive-ui'
import { BuildOutline } from '@vicons/ionicons5'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { sessionsStore } from '../stores/sessions.js'
import {
    calculateTax, screenFunds, exchangeRate, calculateInstallment
} from '../api/tool.js'

const { state } = sessionsStore
const router = useRouter()
const message = useMessage()

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

/* ============== 个税计算器 ============== */

const taxFormRef = ref(null)
const taxLoading = ref(false)
const taxResult = ref(null)

const taxForm = reactive({
    annualIncome: null,
    socialInsurance: null,
    specialDeduction: null
})

// 正数校验规则（金额必须为正数）
function positiveRule(label) {
    return {
        type: 'number',
        required: true,
        validator: (rule, value) => {
            if (value == null) return new Error(`请输入${label}`)
            if (Number(value) <= 0) return new Error(`${label}必须为正数`)
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
            if (Number(value) < 0) return new Error(`${label}不能为负数`)
            return true
        },
        trigger: ['blur', 'change']
    }
}

const taxRules = {
    annualIncome: positiveRule('年薪收入'),
    socialInsurance: nonNegativeRule('社保公积金扣除'),
    specialDeduction: nonNegativeRule('专项附加扣除')
}

async function handleTax() {
    try {
        await taxFormRef.value?.validate()
    } catch {
        return
    }
    taxLoading.value = true
    try {
        const res = await calculateTax({
            annualIncome: taxForm.annualIncome,
            socialInsurance: Number(taxForm.socialInsurance) || 0,
            specialDeduction: Number(taxForm.specialDeduction) || 0
        })
        taxResult.value = unwrap(res) || {}
        message.success('计算完成')
    } catch (e) {
        message.error('计算失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    } finally {
        taxLoading.value = false
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
    { label: '股票型', value: 'stock' },
    { label: '混合型', value: 'mixed' },
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
    maxReturn: nonNegativeRule('最低收益率'),
    maxRiskLevel: {
        type: 'number',
        required: true,
        message: '请选择最大风险等级',
        trigger: ['change', 'blur']
    }
}

// 基金类型中文映射
const FUND_TYPE_LABELS = {
    stock: '股票型', mixed: '混合型', bond: '债券型', index: '指数型'
}

const fundColumns = [
    { title: '基金名称', key: 'name', render: (row) => row.name || row.fundName || '-' },
    { title: '基金代码', key: 'code', render: (row) => row.code || row.fundCode || '-' },
    {
        title: '类型', key: 'type',
        render: (row) => FUND_TYPE_LABELS[row.type] || row.typeName || row.type || '-'
    },
    {
        title: '近一年收益', key: 'returnRate',
        render: (row) => formatPercent(row.returnRate ?? row.yearlyReturn)
    },
    {
        title: '风险等级', key: 'riskLevel',
        render: (row) => `${row.riskLevel ?? '-'} 级`
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
            minReturn: Number(fundForm.minReturn) || 0,
            maxRiskLevel: fundForm.maxRiskLevel
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

/* ============== 信用卡分期计算器 ============== */

const installmentFormRef = ref(null)
const installmentLoading = ref(false)
const installmentResult = ref(null)

const installmentForm = reactive({
    totalAmount: null,
    periods: null,
    annualRate: null
})

const periodOptions = [
    { label: '3 期', value: 3 },
    { label: '6 期', value: 6 },
    { label: '12 期', value: 12 },
    { label: '24 期', value: 24 }
]

const installmentRules = {
    totalAmount: positiveRule('分期总额'),
    periods: { type: 'number', required: true, message: '请选择分期期数', trigger: ['change', 'blur'] },
    annualRate: positiveRule('年化费率')
}

async function handleInstallment() {
    try {
        await installmentFormRef.value?.validate()
    } catch {
        return
    }
    installmentLoading.value = true
    try {
        const res = await calculateInstallment({
            totalAmount: installmentForm.totalAmount,
            periods: installmentForm.periods,
            annualRate: installmentForm.annualRate
        })
        installmentResult.value = unwrap(res) || {}
        message.success('计算完成')
    } catch (e) {
        message.error('计算失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    } finally {
        installmentLoading.value = false
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
:deep(.n-form-item-label__text) { font-size: 16px; }
:deep(.n-input__input-el),
:deep(.n-input-number-input) { font-size: 18px; }
:deep(.n-base-selection-input__el),
:deep(.n-base-selection-label__content) { font-size: 18px; }
:deep(.n-descriptions-table-content) { font-size: 16px; }
:deep(.n-statistic-value__content) { font-size: 22px; }
:deep(.n-statistic .n-statistic__label) { font-size: 16px; }
:deep(.n-data-table-th__title) { font-size: 16px; }
:deep(.n-data-table-td) { font-size: 16px; }
:deep(.n-tabs-tab__label) { font-size: 16px; }

@media (max-width: 768px) {
    .content { padding: 16px; }
    .stat-grid { grid-template-columns: 1fr; }
}
</style>
