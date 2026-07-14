<template>
    <n-config-provider :theme="theme" :locale="zhCN" :date-locale="dateZhCN">
        <n-loading-bar-provider>
            <n-dialog-provider>
                <n-notification-provider>
                    <n-message-provider>
                        <router-view />
                    </n-message-provider>
                </n-notification-provider>
            </n-dialog-provider>
        </n-loading-bar-provider>
    </n-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import {
    NConfigProvider,
    NLoadingBarProvider,
    NDialogProvider,
    NNotificationProvider,
    NMessageProvider,
    darkTheme,
    zhCN,
    dateZhCN
} from 'naive-ui'
import { settings } from './stores/settings.js'

const theme = computed(() => settings.theme === 'dark' ? darkTheme : null)
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
