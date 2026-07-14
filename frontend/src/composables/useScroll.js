import { ref, nextTick, watch } from 'vue'

export function useScroll(deps) {
    const container = ref(null)

    function scrollToBottom() {
        nextTick(() => {
            if (container.value) {
                container.value.scrollTop = container.value.scrollHeight
            }
        })
    }

    if (deps) {
        watch(deps, scrollToBottom, { deep: true })
    }

    return { container, scrollToBottom }
}
