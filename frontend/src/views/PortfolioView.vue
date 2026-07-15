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

        <div class="portfolio-view">
            <div class="content">
                <div class="header-row">
                    <h2 class="title">
                        <WalletOutlined style="font-size: 22px;" />
                        我的资产
                    </h2>
                    <div class="header-actions">
                        <a-button @click="handleDownloadTemplate" :loading="downloadingTemplate">
                            <template #icon><DownloadOutlined /></template>
                            下载模板
                        </a-button>
                        <a-button @click="triggerImport" :loading="importing">
                            <template #icon><CloudUploadOutlined /></template>
                            导入资产
                        </a-button>
                        <a-button type="primary" @click="openCreate">
                            <template #icon><PlusOutlined /></template>
                            添加资产
                        </a-button>
                        <input ref="importFileInputRef" type="file" style="display:none"
                               accept=".xlsx,.xls,.csv"
                               @change="handleImportFile" />
                    </div>
                </div>

                <!-- 概览卡片 -->
                <div class="stats">
                    <div class="stat-card">
                        <div class="stat-value">{{ formatMoney(summary.totalCost) }}</div>
                        <div class="stat-label">总成本</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{{ formatMoney(summary.totalMarketValue) }}</div>
                        <div class="stat-label">总市值</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value" :style="{ color: profitColor(summary.profitLoss) }">
                            {{ formatMoney(summary.profitLoss) }}
                        </div>
                        <div class="stat-label">累计盈亏</div>
                    </div>
                </div>

                <!-- 类型占比 -->
                <div v-if="breakdown.length" class="card">
                    <div class="card-title">类型分布</div>
                    <div class="breakdown">
                        <div v-for="item in breakdown" :key="item.type" class="bd-item">
                            <div class="bd-type">{{ typeLabel(item.type) }}</div>
                            <div class="bd-value">{{ formatMoney(item.marketValue) }}</div>
                            <div class="bd-pct">{{ formatPercent(item.percentage) }}</div>
                        </div>
                    </div>
                </div>

                <!-- 资产明细 -->
                <div class="card">
                    <div class="card-title">资产明细</div>
                    <div v-if="!loading && assets.length === 0" class="empty-state">
                        <WalletOutlined style="font-size: 40px; color: #d9d9e3;" />
                        <p>还没有资产记录，点击「添加资产」开始管理你的投资组合</p>
                        <a-button type="primary" @click="openCreate">添加资产</a-button>
                    </div>
                    <a-table
                        v-else
                        :columns="columns"
                        :data-source="tableData"
                        :loading="loading"
                        :bordered="false"
                        :pagination="false"
                    />
                </div>
            </div>
        </div>

        <!-- 添加/编辑弹窗 -->
        <a-modal
            v-model:open="showModal"
            :title="editingAsset ? '编辑资产' : '添加资产'"
            style="width: 520px; max-width: 90vw;"
            :mask-closable="false"
        >
            <AssetForm
                :asset="editingAsset"
                @submit="handleSubmit"
                @cancel="showModal = false"
            />
        </a-modal>
    </AppLayout>
</template>

<script setup>
import { ref, computed, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { App, Tag, Button, Popconfirm } from 'ant-design-vue'
import {
    WalletOutlined, PlusOutlined, EditOutlined, DeleteOutlined,
    DownloadOutlined, CloudUploadOutlined
} from '@ant-design/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import AssetForm from '../components/AssetForm.vue'
import { sessionsStore } from '../stores/sessions.js'
import {
    listAssets, createAsset, updateAsset, deleteAsset, getSummary,
    importAssets, downloadTemplate
} from '../api/portfolio.js'

const { state } = sessionsStore
const router = useRouter()
const { message } = App.useApp()

const assets = ref([])
const summary = ref({})
const loading = ref(false)
const showModal = ref(false)
const editingAsset = ref(null)
const importing = ref(false)
const downloadingTemplate = ref(false)
const importFileInputRef = ref(null)

const TYPE_LABELS = {
    stock: '股票', fund: '基金', deposit: '存款', bond: '债券', cash: '现金', other: '其他'
}
const TYPE_TAG_COLOR = {
    stock: 'error', fund: 'processing', deposit: 'success', bond: 'warning', cash: 'default', other: 'default'
}

const breakdown = computed(() => summary.value?.breakdown || [])

const tableData = computed(() => assets.value.map(a => {
    const cost = (Number(a.amount) || 0) * (Number(a.costPrice) || 0)
    const marketValue = a.marketValue != null ? Number(a.marketValue) : cost
    return { ...a, _cost: cost, _marketValue: marketValue, _profit: marketValue - cost }
}))

const columns = [
    { title: '名称', dataIndex: 'name', key: 'name', customRender: ({ record }) => record.name || '-' },
    {
        title: '类型', dataIndex: 'type', key: 'type',
        customRender: ({ record }) => h(
            Tag,
            { color: TYPE_TAG_COLOR[record.type] || 'default', bordered: false },
            { default: () => typeLabel(record.type) }
        )
    },
    { title: '代码', dataIndex: 'symbol', key: 'symbol', customRender: ({ record }) => record.symbol || '-' },
    { title: '数量', dataIndex: 'amount', key: 'amount', customRender: ({ record }) => formatNumber(record.amount) },
    { title: '成本价', dataIndex: 'costPrice', key: 'costPrice', customRender: ({ record }) => formatMoney(record.costPrice) },
    { title: '估算市值', dataIndex: '_marketValue', key: 'marketValue', customRender: ({ record }) => formatMoney(record._marketValue) },
    {
        title: '盈亏', dataIndex: '_profit', key: 'profit',
        customRender: ({ record }) => h(
            'span',
            { style: { color: profitColor(record._profit), fontWeight: 600 } },
            formatMoney(record._profit)
        )
    },
    {
        title: '操作', key: 'actions',
        customRender: ({ record }) => h('div', { class: 'action-cell' }, [
            h(
                Button,
                { type: 'link', size: 'small', onClick: () => openEdit(record) },
                {
                    default: () => '编辑',
                    icon: () => h(EditOutlined)
                }
            ),
            h(
                Popconfirm,
                { title: `确定删除「${record.name || record.symbol}」吗？`, onConfirm: () => handleDelete(record) },
                {
                    default: () => h(
                        Button,
                        { type: 'link', size: 'small', danger: true },
                        {
                            default: () => '删除',
                            icon: () => h(DeleteOutlined)
                        }
                    )
                }
            )
        ])
    }
]

function typeLabel(t) {
    return TYPE_LABELS[t] || t || '-'
}

function formatMoney(v) {
    if (v == null || v === '' || isNaN(v)) return '-'
    return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatNumber(v) {
    if (v == null || v === '' || isNaN(v)) return '-'
    return Number(v).toLocaleString('zh-CN')
}

function formatPercent(v) {
    if (v == null || isNaN(v)) return '-'
    let p = Number(v)
    if (p <= 1) p = p * 100 // 兼容 0-1 区间
    return p.toFixed(2) + '%'
}

function profitColor(v) {
    if (v == null || isNaN(v)) return '#8c8c8c'
    if (v > 0) return '#52c41a'
    if (v < 0) return '#f5222d'
    return '#8c8c8c'
}

function triggerImport() {
    importFileInputRef.value?.click()
}

async function handleImportFile(event) {
    const file = event.target.files[0]
    if (!file) return
    importing.value = true
    try {
        const result = await importAssets(file)
        const successCount = result?.success || 0
        const failedItems = result?.failed || []
        if (failedItems.length > 0) {
            const failedText = failedItems.map(f => `第${f.row}行: ${f.reason}`).join('\n')
            message.warning(`成功导入 ${successCount} 条，失败 ${failedItems.length} 条：\n${failedText}`)
        } else {
            message.success(`成功导入 ${successCount} 条资产`)
        }
        await loadAll()
    } catch (err) {
        message.error('导入失败：' + (err?.response?.data?.message || err?.message || '未知错误'))
    } finally {
        importing.value = false
        event.target.value = ''
    }
}

async function handleDownloadTemplate() {
    downloadingTemplate.value = true
    try {
        const res = await downloadTemplate()
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = 'asset_import_template.xlsx'
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        message.success('模板下载成功')
    } catch (err) {
        message.error('下载失败：' + (err?.message || '未知错误'))
    } finally {
        downloadingTemplate.value = false
    }
}

function openCreate() {
    editingAsset.value = null
    showModal.value = true
}

function openEdit(row) {
    editingAsset.value = { ...row }
    showModal.value = true
}

async function handleSubmit(payload) {
    try {
        if (editingAsset.value?.id != null) {
            await updateAsset(editingAsset.value.id, payload)
            message.success('资产已更新')
        } else {
            await createAsset(payload)
            message.success('资产已添加')
        }
        showModal.value = false
        await loadAll()
    } catch (e) {
        message.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
}

async function handleDelete(row) {
    try {
        await deleteAsset(row.id)
        message.success('资产已删除')
        await loadAll()
    } catch (e) {
        message.error('删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
}

async function loadList() {
    loading.value = true
    try {
        assets.value = (await listAssets()) || []
    } catch (e) {
        message.error('加载资产失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
        assets.value = []
    } finally {
        loading.value = false
    }
}

async function loadSummary() {
    try {
        summary.value = (await getSummary()) || {}
    } catch {
        summary.value = {}
    }
}

async function loadAll() {
    await Promise.all([loadList(), loadSummary()])
}

function goChat() {
    router.push('/chat')
}

onMounted(() => {
    loadAll()
})
</script>

<style scoped>
.portfolio-view { height: 100%; overflow-y: auto; background: #ffffff; }
.content { max-width: 1024px; margin: 0 auto; padding: 24px; }
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.header-actions { display: flex; gap: 8px; align-items: center; }
.title { margin: 0; display: flex; align-items: center; gap: 8px; font-size: 20px; color: #202123; font-weight: 600; }
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px; }
.stat-card { padding: 20px; background: #f7f7f8; border-radius: 8px; text-align: center; }
.stat-value { font-size: 24px; font-weight: 700; color: #202123; }
.stat-label { font-size: 12px; color: #6e6f80; margin-top: 4px; }
.card { background: #ffffff; border: 1px solid #ececf1; border-radius: 8px; padding: 16px 20px; margin-bottom: 16px; }
.card-title { font-size: 15px; font-weight: 600; color: #202123; margin-bottom: 12px; }
.breakdown { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; }
.bd-item { padding: 12px; background: #f7f7f8; border-radius: 6px; text-align: center; }
.bd-type { font-size: 13px; color: #6e6f80; }
.bd-value { font-size: 16px; font-weight: 600; color: #202123; margin: 4px 0; }
.bd-pct { font-size: 12px; color: #1890ff; }
.empty-state { padding: 40px 24px; text-align: center; color: #6e6f80; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.empty-state p { margin: 0; font-size: 14px; }
.action-cell { display: flex; gap: 12px; }
@media (max-width: 768px) {
    .stats { grid-template-columns: 1fr; }
}
</style>
