import http from './http.js'

// 个税计算
export const calculateTax = (data) => http.post('/tool/tax', data)

// 基金筛选
export const screenFunds = (data) => http.post('/tool/fund-screener', data)

// 汇率换算
export const exchangeRate = (from, to, amount) => http.get('/tool/exchange-rate', { params: { from, to, amount } })

// 信用卡分期计算
export const calculateInstallment = (data) => http.post('/tool/installment', data)
