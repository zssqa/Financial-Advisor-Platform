<template>
    <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="left"
        label-width="96"
        require-mark-placement="right-hanging"
    >
        <n-form-item label="目标类型" path="type">
            <n-select
                v-model:value="form.type"
                :options="typeOptions"
                placeholder="请选择目标类型"
            />
        </n-form-item>

        <n-form-item label="目标金额" path="targetAmount">
            <n-input-number
                v-model:value="form.targetAmount"
                :min="0"
                :precision="2"
                placeholder="目标金额"
                style="width: 100%"
                clearable
            >
                <template #prefix>¥</template>
            </n-input-number>
        </n-form-item>

        <n-form-item label="当前金额" path="currentAmount">
            <n-input-number
                v-model:value="form.currentAmount"
                :min="0"
                :precision="2"
                placeholder="已存金额"
                style="width: 100%"
                clearable
            >
                <template #prefix>¥</template>
            </n-input-number>
        </n-form-item>

        <n-form-item label="截止日期" path="deadline">
            <n-date-picker
                v-model:value="form.deadline"
                type="date"
                placeholder="选择截止日期"
                style="width: 100%"
                clearable
            />
        </n-form-item>

        <n-form-item label="每月储蓄" path="monthlyContribution">
            <n-input-number
                v-model:value="form.monthlyContribution"
                :min="0"
                :precision="2"
                placeholder="每月计划储蓄额"
                style="width: 100%"
                clearable
            >
                <template #prefix>¥</template>
            </n-input-number>
        </n-form-item>

        <n-form-item label="备注" path="notes">
            <n-input
                v-model:value="form.notes"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                placeholder="可选：目标说明 / 名称"
            />
        </n-form-item>

        <div class="form-actions">
            <n-button @click="emit('cancel')">取消</n-button>
            <n-button type="primary" @click="handleSubmit">
                {{ isEdit ? '保存修改' : '创建目标' }}
            </n-button>
        </div>
    </n-form>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { NForm, NFormItem, NSelect, NInputNumber, NDatePicker, NInput, NButton } from 'naive-ui'

const props = defineProps({
    goal: { type: Object, default: null }
})
const emit = defineEmits(['submit', 'cancel'])

const formRef = ref(null)
const isEdit = computed(() => !!props.goal)

const typeOptions = [
    { label: '退休', value: 'retirement' },
    { label: '教育', value: 'education' },
    { label: '购房', value: 'house' },
    { label: '应急基金', value: 'emergency_fund' },
    { label: '自定义', value: 'custom' }
]

function toNumber(v) {
    if (v === null || v === '' || v === undefined) return null
    const n = Number(v)
    return isNaN(n) ? null : n
}

// 将时间戳转为 "yyyy-MM-dd" 字符串（提交时使用）
function timestampToDateStr(ts) {
    if (!ts) return null
    const d = new Date(ts)
    const yyyy = d.getFullYear()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return `${yyyy}-${mm}-${dd}`
}

const form = reactive({
    type: props.goal?.type || 'retirement',
    targetAmount: toNumber(props.goal?.targetAmount),
    currentAmount: toNumber(props.goal?.currentAmount),
    // n-date-picker 需要时间戳（number），后端返回的字符串需转换
    deadline: props.goal?.deadline ? new Date(props.goal.deadline).getTime() : null,
    monthlyContribution: toNumber(props.goal?.monthlyContribution),
    notes: props.goal?.notes || ''
})

const rules = {
    type: { required: true, message: '请选择目标类型', trigger: ['change', 'blur'] },
    targetAmount: {
        type: 'number',
        required: true,
        message: '请输入目标金额',
        trigger: ['blur', 'change']
    },
    deadline: {
        type: 'number',
        required: true,
        message: '请选择截止日期',
        trigger: ['change', 'blur']
    }
}

async function handleSubmit() {
    try {
        await formRef.value?.validate()
    } catch {
        return
    }
    const payload = {
        type: form.type,
        targetAmount: toNumber(form.targetAmount),
        currentAmount: toNumber(form.currentAmount),
        deadline: timestampToDateStr(form.deadline),
        monthlyContribution: toNumber(form.monthlyContribution),
        notes: form.notes || ''
    }
    emit('submit', payload)
}
</script>

<style scoped>
.form-actions {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 8px;
}
</style>
