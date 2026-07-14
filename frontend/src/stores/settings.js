import { reactive, watch } from 'vue'

const STORAGE_KEY = 'fa_settings'

const defaults = {
    riskLevel: 'R3',
    showReasoning: false
}

function loadFromStorage() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY)
        return raw ? { ...defaults, ...JSON.parse(raw) } : { ...defaults }
    } catch {
        return { ...defaults }
    }
}

export const settings = reactive(loadFromStorage())

watch(settings, (val) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
}, { deep: true })

export function resetSettings() {
    Object.assign(settings, defaults)
}
