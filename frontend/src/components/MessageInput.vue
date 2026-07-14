<template>
    <div class="input-container">
        <!-- File preview area (above textarea) -->
        <div v-if="uploadedFiles.length > 0" class="file-previews">
            <n-tag v-for="(file, index) in uploadedFiles" :key="index"
                   closable @close="removeFile(index)" type="info" size="small">
                {{ file.name }}
            </n-tag>
        </div>
        <div class="input-wrap">
            <textarea
                ref="textareaRef"
                v-model="text"
                class="textarea"
                :placeholder="placeholder"
                :disabled="disabled"
                rows="2"
                @keydown="handleKeydown"
                @input="handleInput"
            ></textarea>
            <n-button quaternary circle @click="triggerFileInput" :loading="uploading" :disabled="disabled">
                <template #icon>
                    <n-icon><AttachOutline /></n-icon>
                </template>
            </n-button>
            <button
                class="send-btn"
                :disabled="disabled || !text.trim()"
                @click="handleSend"
            >
                {{ disabled ? '思考中...' : '发送' }}
            </button>
            <input ref="fileInputRef" type="file" style="display:none"
                   accept=".pdf,.xlsx,.xls,.csv,.md,.txt,.png,.jpg,.jpeg"
                   @change="handleFileSelect" />
        </div>
    </div>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue'
import { AttachOutline } from '@vicons/ionicons5'
import { NButton, NIcon, NTag, useMessage } from 'naive-ui'
import http from '../api/http.js'

const props = defineProps({
    placeholder: { type: String, default: '输入您的理财问题，例如：我有50万闲钱该如何理财？' },
    disabled: { type: Boolean, default: false },
    draft: { type: String, default: '' }
})

const emit = defineEmits(['send', 'update:draft'])

const message = useMessage()
const text = ref('')
const textareaRef = ref(null)
const fileInputRef = ref(null)
const uploadedFiles = ref([])
const uploading = ref(false)

watch(() => props.draft, (v) => {
    if (v !== text.value) text.value = v
}, { immediate: true })

watch(text, (v) => emit('update:draft', v))

function handleKeydown(e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
        e.preventDefault()
        handleSend()
    }
}

function handleInput() {
    emit('update:draft', text.value)
}

function handleSend() {
    const message = text.value.trim()
    if (!message || props.disabled) return
    text.value = ''
    emit('update:draft', '')
    emit('send', message)
    nextTick(() => textareaRef.value?.focus())
}

function focus() {
    textareaRef.value?.focus()
}

function triggerFileInput() {
    fileInputRef.value?.click()
}

async function handleFileSelect(event) {
    const file = event.target.files[0]
    if (!file) return

    // Size check (10MB)
    if (file.size > 10 * 1024 * 1024) {
        message.error('文件大小不能超过10MB')
        return
    }

    uploading.value = true
    const formData = new FormData()
    formData.append('file', file)

    try {
        // 使用共享 http 实例，自动附带 Bearer token；不手动设 Content-Type，让浏览器生成 boundary
        const response = await http.post('/chat/upload', formData, { timeout: 120000 })
        const data = response.data
        if (data?.error) {
            message.error(data.error || '上传失败')
            return
        }
        uploadedFiles.value.push({
            fileId: data.fileId,
            name: data.fileName,
            type: data.fileType,
            textContent: data.textContent
        })
        message.success(`文件 ${data.fileName} 上传成功`)
    } catch (err) {
        const msg = err?.response?.data?.error || err.message
        message.error('上传失败: ' + msg)
    } finally {
        uploading.value = false
        event.target.value = ''  // reset input
    }
}

function removeFile(index) {
    uploadedFiles.value.splice(index, 1)
}

function getFiles() {
    return uploadedFiles.value
}

function clearFiles() {
    uploadedFiles.value = []
}

defineExpose({ focus, getFiles, clearFiles })
</script>

<style scoped>
.input-container {
    border-top: 1px solid #ececf1;
}
.file-previews {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 8px 0 0;
}
.input-wrap {
    display: flex; gap: 8px; align-items: flex-end;
    padding: 12px 0;
}
.textarea {
    flex: 1;
    resize: none;
    padding: 12px 14px;
    border: 1px solid #d9d9e3;
    border-radius: 8px;
    font-size: 20px;
    font-family: inherit;
    line-height: 1.7;
    outline: none;
    transition: border-color 0.2s;
    max-height: 200px;
    background: #ffffff;
    color: #202123;
}
.textarea:focus { border-color: #10a37f; }
.textarea:disabled { background: #f7f7f8; cursor: not-allowed; }
.send-btn {
    padding: 10px 20px;
    background: #10a37f;
    color: white;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    font-size: 20px;
    font-weight: 500;
    transition: background 0.15s;
    height: 50px;
}
.send-btn:hover:not(:disabled) { background: #0e8c6d; }
.send-btn:disabled { background: #c5c5d2; cursor: not-allowed; }
</style>
