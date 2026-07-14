import axios from 'axios'
import { auth } from '../stores/auth.js'

const http = axios.create({
    baseURL: '/api',
    timeout: 30000
})

// 请求拦截器：附带 Bearer token
http.interceptors.request.use(
    (config) => {
        if (auth.token) {
            config.headers.Authorization = 'Bearer ' + auth.token
        }
        return config
    },
    (error) => Promise.reject(error)
)

// 响应拦截器：401 时清除登录态并跳转登录页
http.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error?.response?.status
        if (status === 401) {
            auth.clear()
            // 避免在登录页本身循环跳转
            if (window.location.pathname !== '/login') {
                window.location.href = '/login'
            }
        }
        return Promise.reject(error)
    }
)

export default http
