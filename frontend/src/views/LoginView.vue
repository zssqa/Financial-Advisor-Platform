<template>
    <div class="login-page">
        <n-card class="login-card" :bordered="false">
            <div class="brand">
                <n-icon size="32" color="#18a058"><CashOutline /></n-icon>
                <span class="brand-title">金融理财顾问</span>
            </div>

            <n-tabs v-model:value="mode" type="line" animated>
                <n-tab-pane name="login" tab="登录">
                    <n-form
                        ref="loginFormRef"
                        :model="loginForm"
                        :rules="loginRules"
                        label-placement="top"
                        size="large"
                    >
                        <n-form-item label="用户名" path="username">
                            <n-input
                                v-model:value="loginForm.username"
                                placeholder="请输入用户名"
                                clearable
                                @keyup.enter="handleLogin"
                            />
                        </n-form-item>
                        <n-form-item label="密码" path="password">
                            <n-input
                                v-model:value="loginForm.password"
                                type="password"
                                show-password-on="click"
                                placeholder="请输入密码"
                                @keyup.enter="handleLogin"
                            />
                        </n-form-item>
                        <n-button
                            type="primary"
                            block
                            :loading="loading"
                            @click="handleLogin"
                        >
                            登录
                        </n-button>
                    </n-form>
                </n-tab-pane>

                <n-tab-pane name="register" tab="注册">
                    <n-form
                        ref="registerFormRef"
                        :model="registerForm"
                        :rules="registerRules"
                        label-placement="top"
                        size="large"
                    >
                        <n-form-item label="用户名" path="username">
                            <n-input
                                v-model:value="registerForm.username"
                                placeholder="请输入用户名"
                                clearable
                            />
                        </n-form-item>
                        <n-form-item label="密码" path="password">
                            <n-input
                                v-model:value="registerForm.password"
                                type="password"
                                show-password-on="click"
                                placeholder="请输入密码"
                            />
                        </n-form-item>
                        <n-form-item label="确认密码" path="confirmPassword">
                            <n-input
                                v-model:value="registerForm.confirmPassword"
                                type="password"
                                show-password-on="click"
                                placeholder="请再次输入密码"
                                @keyup.enter="handleRegister"
                            />
                        </n-form-item>
                        <n-button
                            type="primary"
                            block
                            :loading="loading"
                            @click="handleRegister"
                        >
                            注册
                        </n-button>
                    </n-form>
                </n-tab-pane>
            </n-tabs>
        </n-card>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
    NCard, NTabs, NTabPane, NForm, NFormItem, NInput, NButton, NIcon
} from 'naive-ui'
import { CashOutline } from '@vicons/ionicons5'
import { login, register } from '../api/auth.js'
import { auth } from '../stores/auth.js'

const router = useRouter()
const route = useRoute()
const message = useMessage()

const mode = ref('login')
const loading = ref(false)

const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', confirmPassword: '' })

const loginRules = {
    username: { required: true, message: '请输入用户名', trigger: ['blur', 'input'] },
    password: { required: true, message: '请输入密码', trigger: ['blur', 'input'] }
}

const registerRules = {
    username: { required: true, message: '请输入用户名', trigger: ['blur', 'input'] },
    password: { required: true, message: '请输入密码', trigger: ['blur', 'input'] },
    confirmPassword: {
        required: true,
        trigger: ['blur', 'input'],
        validator: (rule, value) => {
            if (!value) return new Error('请再次输入密码')
            if (value !== registerForm.password) return new Error('两次输入的密码不一致')
            return true
        }
    }
}

function redirectAfterAuth() {
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/chat')
}

async function handleLogin() {
    try {
        await loginFormRef.value?.validate()
    } catch {
        return
    }
    loading.value = true
    try {
        const data = await login(loginForm.username, loginForm.password)
        auth.setAuth(data.token, {
            userId: data.userId,
            username: data.username,
            riskLevel: data.riskLevel
        })
        message.success('登录成功')
        redirectAfterAuth()
    } catch (err) {
        message.error(err?.response?.data?.message || err?.message || '登录失败')
    } finally {
        loading.value = false
    }
}

async function handleRegister() {
    try {
        await registerFormRef.value?.validate()
    } catch {
        return
    }
    loading.value = true
    try {
        const data = await register(registerForm.username, registerForm.password)
        auth.setAuth(data.token, {
            userId: data.userId,
            username: data.username,
            riskLevel: data.riskLevel
        })
        message.success('注册成功')
        redirectAfterAuth()
    } catch (err) {
        message.error(err?.response?.data?.message || err?.message || '注册失败')
    } finally {
        loading.value = false
    }
}
</script>

<style scoped>
.login-page {
    height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #f0f9eb 0%, #e8f4f8 100%);
}
.login-card {
    width: 400px;
    max-width: 90vw;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
    border-radius: 12px;
}
.brand {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-bottom: 20px;
}
.brand-title {
    font-size: 22px;
    font-weight: 700;
    color: #202123;
}
</style>
