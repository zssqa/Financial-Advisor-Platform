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

        <div class="goal-view">
            <div class="content">
                <div class="header">
                    <h2 class="title">
                        <TrophyOutlined :style="{ fontSize: '22px' }" />
                        理财目标
                    </h2>
                    <a-button type="primary" @click="openCreate">
                        <template #icon><PlusOutlined /></template>
                        新建目标
                    </a-button>
                </div>

                <a-spin :spinning="loading">
                    <div v-if="goals.length" class="goal-grid">
                        <div v-for="goal in goals" :key="goal.id" class="goal-card">
                            <div class="card-head">
                                <div class="head-left">
                                    <component
                                        :is="typeIcons[goal.type] || TrophyOutlined"
                                        :style="{ fontSize: '24px', color: typeColor(goal.type) }"
                                    />
                                    <div class="head-titles">
                                        <div class="goal-type">{{ typeLabels[goal.type] || '自定义' }}</div>
                                        <div v-if="goal.notes" class="goal-name" :title="goal.notes">
                                            {{ goal.notes }}
                                        </div>
                                    </div>
                                </div>
                                <div class="head-actions">
                                    <a-button type="text" size="small" @click="openEdit(goal)">
                                        <template #icon><EditOutlined /></template>
                                        编辑
                                    </a-button>
                                    <a-popconfirm title="确认删除该目标？此操作不可恢复。" @confirm="handleDelete(goal)">
                                        <a-button type="text" danger size="small">
                                            <template #icon><DeleteOutlined /></template>
                                            删除
                                        </a-button>
                                    </a-popconfirm>
                                </div>
                            </div>

                            <div class="progress-wrap">
                                <a-progress
                                    type="line"
                                    :percent="clampPct(summaryOf(goal).progressPercent)"
                                    :status="progressStatus(summaryOf(goal).progressPercent)"
                                    :strokeWidth="10"
                                />
                            </div>

                            <div class="amount-row">
                                <div class="amount-block">
                                    <div class="amount-label">目标金额</div>
                                    <div class="amount-value">{{ formatMoney(goal.targetAmount) }}</div>
                                </div>
                                <div class="amount-block">
                                    <div class="amount-label">已存金额</div>
                                    <div class="amount-value saved">{{ formatMoney(goal.currentAmount) }}</div>
                                </div>
                            </div>

                            <div class="meta-row">
                                <div class="meta-item">
                                    <ClockCircleOutlined :style="{ fontSize: '14px', color: '#6e6f80' }" />
                                    <span>剩余 {{ summaryOf(goal).monthsRemaining ?? '-' }} 个月</span>
                                </div>
                                <div class="meta-item">
                                    <DollarOutlined :style="{ fontSize: '14px', color: '#6e6f80' }" />
                                    <span>每月还需 {{ formatMoney(summaryOf(goal).monthlyNeeded) }}</span>
                                </div>
                            </div>

                            <div class="deadline-row">
                                <CalendarOutlined :style="{ fontSize: '14px', color: '#6e6f80' }" />
                                <span>截止日期：{{ formatDate(goal.deadline) }}</span>
                            </div>
                        </div>
                    </div>

                    <div v-else-if="!loading" class="empty-state">
                        <a-empty description="还没有理财目标，开始规划你的第一个目标吧">
                            <a-button type="primary" @click="openCreate">
                                <template #icon><PlusOutlined /></template>
                                新建目标
                            </a-button>
                        </a-empty>
                    </div>

                    <div v-else class="grid-placeholder"></div>
                </a-spin>
            </div>
        </div>

        <a-modal
            v-model:open="showForm"
            :title="editingGoal ? '编辑目标' : '新建目标'"
            style="width: 480px; max-width: 90vw"
            :maskClosable="false"
        >
            <a-spin :spinning="submitting">
                <GoalForm
                    :key="formKey"
                    :goal="editingGoal"
                    @submit="handleSubmit"
                    @cancel="closeForm"
                />
            </a-spin>
        </a-modal>
    </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { App } from 'ant-design-vue'
import {
    TrophyOutlined, PlusOutlined, EditOutlined, DeleteOutlined,
    RocketOutlined, ReadOutlined, HomeOutlined, SafetyOutlined,
    CalendarOutlined, ClockCircleOutlined, DollarOutlined
} from '@ant-design/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import GoalForm from '../components/GoalForm.vue'
import { sessionsStore } from '../stores/sessions.js'
import { listGoals, createGoal, updateGoal, deleteGoal, getSummary } from '../api/goal.js'

const { state } = sessionsStore
const router = useRouter()
const { message } = App.useApp()

const loading = ref(false)
const submitting = ref(false)
const goals = ref([])
const summaryMap = ref({})

const showForm = ref(false)
const editingGoal = ref(null)
const formKey = ref(0)

const typeLabels = {
    retirement: '退休',
    education: '教育',
    house: '购房',
    emergency_fund: '应急基金',
    custom: '自定义'
}

const typeIcons = {
    retirement: RocketOutlined,
    education: ReadOutlined,
    house: HomeOutlined,
    emergency_fund: SafetyOutlined,
    custom: EditOutlined
}

const typeColors = {
    retirement: '#722ed1',
    education: '#1890ff',
    house: '#13c2c2',
    emergency_fund: '#fa8c16',
    custom: '#8c8c8c'
}

function typeColor(t) {
    return typeColors[t] || '#10a37f'
}

function summaryOf(goal) {
    return summaryMap.value[goal.id] || {}
}

function clampPct(v) {
    const n = Number(v)
    if (isNaN(n)) return 0
    return Math.max(0, Math.min(100, n))
}

function progressStatus(v) {
    const n = clampPct(v)
    if (n < 33) return 'error'
    if (n < 80) return 'warning'
    return 'success'
}

function formatMoney(v) {
    if (v == null || v === '') return '-'
    const n = Number(v)
    if (isNaN(n)) return '-'
    return '¥' + n.toLocaleString()
}

function formatDate(v) {
    if (!v) return '-'
    const s = String(v)
    return s.length >= 10 ? s.slice(0, 10) : s
}

function openCreate() {
    editingGoal.value = null
    formKey.value++
    showForm.value = true
}

function openEdit(goal) {
    editingGoal.value = goal
    formKey.value++
    showForm.value = true
}

function closeForm() {
    showForm.value = false
    editingGoal.value = null
}

async function loadAll() {
    loading.value = true
    try {
        const [list, summary] = await Promise.all([
            listGoals().catch(() => []),
            getSummary().catch(() => null)
        ])
        goals.value = Array.isArray(list) ? list : []
        const map = {}
        const items = summary?.goals || []
        for (const item of items) {
            const id = item?.goal?.id
            if (id != null) map[id] = item
        }
        summaryMap.value = map
    } finally {
        loading.value = false
    }
}

async function handleSubmit(payload) {
    submitting.value = true
    try {
        if (editingGoal.value) {
            await updateGoal(editingGoal.value.id, payload)
            message.success('目标已更新')
        } else {
            await createGoal(payload)
            message.success('目标已创建')
        }
        closeForm()
        await loadAll()
    } catch (e) {
        message.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    } finally {
        submitting.value = false
    }
}

async function handleDelete(goal) {
    try {
        await deleteGoal(goal.id)
        message.success('目标已删除')
        await loadAll()
    } catch (e) {
        message.error('删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
}

function goChat() {
    router.push('/chat')
}

onMounted(() => {
    loadAll()
})
</script>

<style scoped>
.goal-view { height: 100%; overflow-y: auto; background: #ffffff; }
.content { max-width: 1024px; margin: 0 auto; padding: 24px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.title { margin: 0; display: flex; align-items: center; gap: 8px; font-size: 20px; color: #202123; font-weight: 600; }
.goal-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.goal-card {
    background: #ffffff;
    border: 1px solid #ececf1;
    border-radius: 10px;
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    transition: box-shadow 0.15s, border-color 0.15s;
}
.goal-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.06); border-color: #d9d9e3; }
.card-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 8px; }
.head-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
.head-titles { min-width: 0; }
.goal-type { font-size: 16px; font-weight: 600; color: #202123; }
.goal-name {
    font-size: 12px;
    color: #6e6f80;
    margin-top: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 180px;
}
.head-actions { display: flex; gap: 4px; flex-shrink: 0; }
.progress-wrap { padding: 2px 0; }
.amount-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.amount-block { background: #f7f7f8; border-radius: 8px; padding: 10px 12px; }
.amount-label { font-size: 12px; color: #6e6f80; }
.amount-value { font-size: 16px; font-weight: 600; color: #202123; margin-top: 4px; }
.amount-value.saved { color: #10a37f; }
.meta-row { display: flex; flex-direction: column; gap: 6px; }
.meta-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #565869; }
.deadline-row {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #6e6f80;
    padding-top: 8px;
    border-top: 1px solid #f7f7f8;
}
.empty-state { padding: 60px 0; }
.grid-placeholder { min-height: 200px; }
@media (max-width: 768px) {
    .content { padding: 16px; }
    .goal-grid { grid-template-columns: 1fr; }
    .goal-name { max-width: none; }
}
</style>
