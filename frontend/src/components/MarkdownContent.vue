<template>
    <div class="markdown-body" v-html="html"></div>
</template>

<script setup>
import { computed } from 'vue'
import { useMarkdown } from '../composables/useMarkdown.js'

const props = defineProps({
    content: { type: String, default: '' }
})

const { render } = useMarkdown()
const html = computed(() => render(props.content))
</script>

<style scoped>
.markdown-body :deep(p) { margin: 6px 0; line-height: 1.7; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 24px; }
.markdown-body :deep(blockquote) {
    margin: 8px 0;
    padding: 6px 12px;
    border-left: 3px solid #10a37f;
    background: #f7f7f8;
    color: #565869;
}
.markdown-body :deep(code) {
    background: #f2f4f7;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 13px;
    font-family: 'SFMono-Regular', Consolas, monospace;
}
.markdown-body :deep(pre) {
    background: #f6f8fa;
    border-radius: 6px;
    padding: 12px;
    overflow-x: auto;
    margin: 8px 0;
}
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
.markdown-body :deep(a) { color: #10a37f; text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(table) {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;
}
.markdown-body :deep(th), .markdown-body :deep(td) {
    border: 1px solid #d0d5dd;
    padding: 6px 12px;
    font-size: 14px;
}
.markdown-body :deep(th) { background: #f2f4f7; font-weight: 600; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
    margin: 12px 0 6px;
    font-weight: 600;
}
</style>
