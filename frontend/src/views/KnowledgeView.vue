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
                    <CloudUploadOutlined style="font-size: 22px;" />
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
                    <a-input
                        v-model:value="searchQuery"
                        placeholder="搜索文档..."
                        allow-clear
                        style="max-width: 360px;"
                        @keydown.enter="handleSearch"
                        @change="e => { if (!e.target.value) loadDocuments() }"
                    >
                        <template #prefix>
                            <SearchOutlined />
                        </template>
                    </a-input>
                    <a-upload
                        :show-upload-list="false"
                        :before-upload="handleUpload"
                        accept=".pdf,.PDF,.xlsx,.xls,.csv,.md,.txt"
                        multiple
                    >
                        <a-button type="primary">
                            <template #icon><CloudUploadOutlined /></template>
                            上传文档
                        </a-button>
                    </a-upload>
                </div>

                <a-card title="联网摄入" size="small" style="margin-bottom: 16px;">
                    <a-space>
                        <a-input
                            v-model:value="ingestKeyword"
                            placeholder="输入关键词，如：2026年A股市场行情"
                            style="width: 300px;"
                            @keydown.enter="handleIngestFromWeb"
                        />
                        <a-button type="primary" :loading="ingesting" @click="handleIngestFromWeb">
                            <template #icon><GlobalOutlined /></template>
                            联网摄入
                        </a-button>
                    </a-space>
                </a-card>

                <a-table
                    :columns="columns"
                    :data-source="documents"
                    :loading="loading"
                    :bordered="false"
                    :pagination="false"
                />

                <div v-if="!loading && documents.length === 0" class="empty">
                    <CloudUploadOutlined style="font-size: 64px; color: #c5c5d2;" />
                    <p>暂无文档，请上传 PDF/Excel/CSV/Markdown/TXT 文件</p>
                </div>
            </div>
        </div>
    </AppLayout>
</template>

<script setup>
import { ref, h, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { App } from 'ant-design-vue'
import { CloudUploadOutlined, SearchOutlined, FileTextOutlined, GlobalOutlined } from '@ant-design/icons-vue'
import AppLayout from '../components/AppLayout.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import { listDocuments, uploadDocument, searchDocuments, getStats, ingestFromWeb } from '../api/documents.js'
import { sessionsStore } from '../stores/sessions.js'

const { state } = sessionsStore
const router = useRouter()
const { message } = App.useApp()

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
        dataIndex: 'filename',
        key: 'filename',
        customRender({ record }) {
            return h('div', { style: 'display:flex;align-items:center;gap:6px;' }, [
                h(FileTextOutlined, { style: { fontSize: '16px', color: '#10a37f' } }),
                h('span', record.filename || record.name || record.title || '-')
            ])
        }
    },
    { title: '大小', dataIndex: 'file_size', key: 'file_size', customRender({ record }) { return formatSize(record.file_size ?? record.size) } },
    { title: '分类', dataIndex: 'category', key: 'category', customRender({ record }) { return record.category || '-' } },
    {
        title: '上传时间',
        dataIndex: 'upload_time',
        key: 'upload_time',
        customRender({ record }) {
            const t = record.upload_time || record.createdAt || record.uploadTime
            return t ? new Date(t).toLocaleString() : '-'
        }
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        customRender({ record }) {
            // 适配后端实际 status 值：indexed/processing/failed
            const status = record.status || 'indexed'
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
        const result = await uploadDocument(file)
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
