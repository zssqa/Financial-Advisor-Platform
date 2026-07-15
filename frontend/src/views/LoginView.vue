<template>
    <div class="login-page">
        <a-card class="login-card" :bordered="false">
            <div class="brand">
                <DollarOutlined :style="{ fontSize: '32px', color: '#18a058' }" />
                <span class="brand-title">金融理财顾问</span>
            </div>

            <a-tabs v-model:activeKey="mode">
                <a-tab-pane key="login" tab="登录">
                    <a-form
                        ref="loginFormRef"
                        :model="loginForm"
                        :rules="loginRules"
                        layout="vertical"
                    >
                        <a-form-item label="用户名" name="username">
                            <a-input
                                v-model:value="loginForm.username"
                                placeholder="请输入用户名"
                                allow-clear
                                @keyup.enter="handleLogin"
                            />
                        </a-form-item>
                        <a-form-item label="密码" name="password">
                            <a-input-password
                                v-model:value="loginForm.password"
                                placeholder="请输入密码"
                                @keyup.enter="handleLogin"
                            />
                        </a-form-item>
                        <a-button
                            type="primary"
                            block
                            :loading="loading"
                            @click="handleLogin"
                        >
                            登录
                        </a-button>
                    </a-form>
                </a-tab-pane>

                <a-tab-pane key="register" tab="注册">
                    <a-form
                        ref="registerFormRef"
                        :model="registerForm"
                        :rules="registerRules"
                        layout="vertical"
                    >
                        <a-form-item label="用户名" name="username">
                            <a-input
                                v-model:value="registerForm.username"
                                placeholder="请输入用户名"
                                allow-clear
                            />
                        </a-form-item>
                        <a-form-item label="密码" name="password">
                            <a-input-password
                                v-model:value="registerForm.password"
                                placeholder="请输入密码"
                            />
                        </a-form-item>
                        <a-form-item label="确认密码" name="confirmPassword">
                            <a-input-password
                                v-model:value="registerForm.confirmPassword"
                                placeholder="请再次输入密码"
                                @keyup.enter="handleRegister"
                            />
                        </a-form-item>
                        <a-button
                            type="primary"
                            block
                            :loading="loading"
                            @click="handleRegister"
                        >
                            注册
                        </a-button>
                    </a-form>
                </a-tab-pane>
            </a-tabs>
        </a-card>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { App } from 'ant-design-vue'
import { DollarOutlined } from '@ant-design/icons-vue'
import { login, register } from '../api/auth.js'
import { auth } from '../stores/auth.js'

const router = useRouter()
const route = useRoute()
const { message } = App.useApp()

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
