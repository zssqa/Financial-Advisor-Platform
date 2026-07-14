<template>
    <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="left"
        label-width="80"
        require-mark-placement="right-hanging"
    >
        <n-form-item label="类型" path="type">
            <n-select v-model:value="form.type" :options="typeOptions" placeholder="选择资产类型" />
        </n-form-item>
        <n-form-item label="代码" path="symbol">
            <n-input v-model:value="form.symbol" placeholder="如 600519 / 000001" />
        </n-form-item>
        <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="资产名称" />
        </n-form-item>
        <n-form-item label="数量" path="amount">
            <n-input-number
                v-model:value="form.amount"
                :min="0"
                :step="1"
                style="width: 100%"
                placeholder="持有数量"
            />
        </n-form-item>
        <n-form-item label="成本价" path="costPrice">
            <n-input-number
                v-model:value="form.costPrice"
                :min="0"
                :step="0.01"
                style="width: 100%"
                placeholder="单份成本价"
            />
        </n-form-item>
        <n-form-item label="买入日期" path="buyDate">
            <n-date-picker
                v-model:value="form.buyDate"
                type="date"
                style="width: 100%"
                placeholder="选择买入日期"
            />
        </n-form-item>
        <n-form-item label="备注" path="notes">
            <n-input
                v-model:value="form.notes"
                type="textarea"
                :rows="3"
                placeholder="备注（可选）"
            />
        </n-form-item>
        <div class="form-actions">
            <n-button @click="emit('cancel')">取消</n-button>
            <n-button type="primary" @click="handleSubmit">确定</n-button>
        </div>
    </n-form>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { NForm, NFormItem, NInput, NInputNumber, NSelect, NDatePicker, NButton } from 'naive-ui'

const props = defineProps({
    asset: { type: Object, default: null }
})
const emit = defineEmits(['submit', 'cancel'])

const formRef = ref(null)

const typeOptions = [
    { label: '股票', value: 'stock' },
    { label: '基金', value: 'fund' },
    { label: '存款', value: 'deposit' },
    { label: '债券', value: 'bond' },
    { label: '现金', value: 'cash' },
    { label: '其他', value: 'other' }
]

const form = reactive({
    type: 'stock',
    symbol: '',
    name: '',
    amount: null,
    costPrice: null,
    buyDate: null,
    notes: ''
})

// 将时间戳转为 "yyyy-MM-dd" 字符串（提交时使用）
function timestampToDateStr(ts) {
    if (!ts) return null
    const d = new Date(ts)
    const yyyy = d.getFullYear()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return `${yyyy}-${mm}-${dd}`
}

const rules = {
    type: { required: true, message: '请选择类型', trigger: ['change', 'blur'] },
    symbol: { required: true, message: '请输入代码', trigger: ['input', 'blur'] },
    name: { required: true, message: '请输入名称', trigger: ['input', 'blur'] },
    amount: { required: true, type: 'number', message: '请输入数量', trigger: ['input', 'blur'] },
    costPrice: { required: true, type: 'number', message: '请输入成本价', trigger: ['input', 'blur'] },
    buyDate: { required: true, type: 'number', message: '请选择买入日期', trigger: ['change', 'blur'] }
}

watch(
    () => props.asset,
    (val) => {
        if (val) {
            form.type = val.type || 'stock'
            form.symbol = val.symbol || ''
            form.name = val.name || ''
            form.amount = val.amount ?? null
            form.costPrice = val.costPrice ?? null
            // 后端返回的是字符串 "yyyy-MM-dd"，需要转为时间戳供 n-date-picker 使用
            form.buyDate = val.buyDate ? new Date(val.buyDate).getTime() : null
            form.notes = val.notes || ''
        } else {
            form.type = 'stock'
            form.symbol = ''
            form.name = ''
            form.amount = null
            form.costPrice = null
            form.buyDate = null
            form.notes = ''
        }
    },
    { immediate: true }
)

async function handleSubmit() {
    try {
        await formRef.value?.validate()
    } catch {
        return
    }
    emit('submit', {
        type: form.type,
        symbol: form.symbol,
        name: form.name,
        amount: form.amount,
        costPrice: form.costPrice,
        buyDate: timestampToDateStr(form.buyDate),
        notes: form.notes
    })
}
</script>

<style scoped>
.form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
}
</style>
