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

        <div class="knowledge-view">
            <div class="content">
                <h2 class="title">
                    <n-icon size="22"><CloudUploadOutline /></n-icon>
                    知识库管理
                </h2>

                <div class="stats">
                    <div class="stat-card">
                        <div class="stat-value">{{ stats.total_documents ?? '-' }}</div>
                        <div class="stat-label">文档总数</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-value">{{ categoryCount }}</div>
                        <div class="stat-label">分类数</div>
                    </div>
                </div>

                <div class="actions">
                    <n-input
                        v-model:value="searchQuery"
                        placeholder="搜索文档..."
                        clearable
                        style="max-width: 360px;"
                        @keydown.enter="handleSearch"
                        @clear="loadDocuments"
                    >
                        <template #prefix>
                            <n-icon><SearchOutline /></n-icon>
                        </template>
                    </n-input>
                    <n-upload
                        :show-file-list="false"
                        :before-upload="handleUpload"
                        accept=".pdf,.PDF,.xlsx,.xls,.csv,.md,.txt"
                        multiple
                    >
                        <n-button type="primary">
                            <template #icon><n-icon><CloudUploadOutline /></n-icon></template>
                            上传文档
                        </n-button>
                    </n-upload>
                </div>

                <n-card title="联网摄入" size="small" style="margin-bottom: 16px;">
                    <n-space>
                        <n-input
                            v-model:value="ingestKeyword"
                            placeholder="输入关键词，如：2026年A股市场行情"
                            style="width: 300px;"
                            @keydown.enter="handleIngestFromWeb"
                        />
                        <n-button type="primary" :loading="ingesting" @click="handleIngestFromWeb">
                            <template #icon><n-icon><GlobeOutline /></n-icon></template>
                            联网摄入
                        </n-button>
                    </n-space>
                </n-card>

                <n-data-table
                    :columns="columns"
                    :data="documents"
                    :loading="loading"
                    :bordered="false"
                    striped
                />

                <div v-if="!loading && documents.length === 0" class="empty">
                    <n-icon size="64" color="#c5c5d2"><CloudUploadOutline /></n-icon>
                    <p>暂无文档，请上传 PDF/Excel/CSV/Markdown/TXT 文件</p>
                </div>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, h, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NIcon, NInput, NButton, NUpload, NDataTable, NCard, NSpace, useMessage } from 'naive-ui'
import { CloudUploadOutline, SearchOutline, DocumentTextOutline, GlobeOutline } from '@vicons/ionicons5'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { listDocuments, uploadDocument, searchDocuments, getStats, ingestFromWeb } from '../api/documents.js'
import { sessionsStore } from '../stores/sessions.js'

const { state } = sessionsStore
const router = useRouter()
const message = useMessage()

const documents = ref([])
const stats = ref({})
const loading = ref(false)
const searchQuery = ref('')
const ingestKeyword = ref('')
const ingesting = ref(false)

// 分类数从后端 type_statistics 数组长度计算（后端 getStatistics 不直接返回 total_categories）
const categoryCount = computed(() => {
    const arr = stats.value?.type_statistics
    return Array.isArray(arr) ? arr.length : 0
})

const columns = [
    {
        title: '文件名',
        key: 'filename',
        render(row) {
            return h('div', { style: 'display:flex;align-items:center;gap:6px;' }, [
                h(NIcon, { size: 16, color: '#10a37f' }, () => h(DocumentTextOutline)),
                h('span', row.filename || row.name || row.title || '-')
            ])
        }
    },
    { title: '大小', key: 'file_size', render(row) { return formatSize(row.file_size ?? row.size) } },
    { title: '分类', key: 'category', render(row) { return row.category || '-' } },
    {
        title: '上传时间',
        key: 'upload_time',
        render(row) {
            const t = row.upload_time || row.createdAt || row.uploadTime
            return t ? new Date(t).toLocaleString() : '-'
        }
    },
    {
        title: '状态',
        key: 'status',
        render(row) {
            // 适配后端实际 status 值：indexed/processing/failed
            const status = row.status || 'indexed'
            const map = {
                indexed: { text: '已导入', color: '#10a37f' },
                processing: { text: '处理中', color: '#d46b08' },
                failed: { text: '失败', color: '#ff4d4f' },
                ready: { text: '已导入', color: '#10a37f' }
            }
            const info = map[status] || { text: status, color: '#6e6f80' }
            return h('span', { style: `color: ${info.color};` }, info.text)
        }
    }
]

function formatSize(bytes) {
    if (!bytes) return '-'
    const units = ['B', 'KB', 'MB', 'GB']
    let i = 0
    let size = bytes
    while (size >= 1024 && i < units.length - 1) {
        size /= 1024
        i++
    }
    return size.toFixed(1) + ' ' + units[i]
}

async function loadDocuments() {
    loading.value = true
    try {
        const data = await listDocuments()
        documents.value = Array.isArray(data) ? data : (data?.data || data?.list || [])
    } catch (e) {
        message.error('加载文档列表失败: ' + e.message)
    } finally {
        loading.value = false
    }
}

async function loadStats() {
    try {
        const data = await getStats()
        stats.value = data?.data || data || {}
    } catch { /* ignore */ }
}

async function handleSearch() {
    const q = searchQuery.value.trim()
    if (!q) {
        loadDocuments()
        return
    }
    loading.value = true
    try {
        const data = await searchDocuments(q)
        documents.value = Array.isArray(data) ? data : (data?.data || data?.list || [])
    } catch (e) {
        message.error('搜索失败: ' + e.message)
    } finally {
        loading.value = false
    }
}

async function handleUpload(file) {
    try {
        const result = await uploadDocument(file.file)
        if (result?.success || result?.code === 0) {
            message.success(result?.message || '上传成功')
            await Promise.all([loadDocuments(), loadStats()])
        } else {
            message.error(result?.message || '上传失败')
        }
    } catch (e) {
        message.error('上传失败: ' + e.message)
    }
    return false
}

async function handleIngestFromWeb() {
    if (!ingestKeyword.value.trim()) {
        message.warning('请输入关键词')
        return
    }
    ingesting.value = true
    try {
        const res = await ingestFromWeb(ingestKeyword.value.trim())
        message.success('联网摄入完成')
        // 刷新文档列表与统计
        await Promise.all([loadDocuments(), loadStats()])
    } catch (err) {
        message.error('摄入失败: ' + (err.response?.data?.message || err.message))
    } finally {
        ingesting.value = false
    }
}

function goChat() {
    router.push('/chat')
}

onMounted(() => {
    loadDocuments()
    loadStats()
})
</script>

<style scoped>
.knowledge-view { height: 100%; overflow-y: auto; background: #ffffff; }
.content { max-width: 1024px; margin: 0 auto; padding: 24px; }
.title { margin: 0 0 24px; display: flex; align-items: center; gap: 8px; font-size: 20px; color: #202123; font-weight: 600; }
.stats { display: flex; gap: 12px; margin-bottom: 16px; }
.stat-card { flex: 1; padding: 16px; background: #f7f7f8; border-radius: 8px; text-align: center; }
.stat-value { font-size: 24px; font-weight: 700; color: #10a37f; }
.stat-label { font-size: 12px; color: #6e6f80; margin-top: 4px; }
.actions { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.empty { text-align: center; padding: 60px 20px; color: #6e6f80; }
.empty p { margin: 12px 0 0; font-size: 14px; }
</style>
