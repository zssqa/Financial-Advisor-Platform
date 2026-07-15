<template>
    <a-config-provider :locale="zhCN" :theme="themeConfig">
        <a-app>
            <router-view />
        </a-app>
    </a-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import { theme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { settings } from './stores/settings.js'

const themeConfig = computed(() => ({
    algorithm: settings.theme === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm
}))
</script>

<style>
* { box-sizing: border-box; }
body {
    margin: 0;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
}
html, body, #app { height: 100%; }
</style>
