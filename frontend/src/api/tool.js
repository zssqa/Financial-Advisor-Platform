import http from './http.js'

// 基金筛选
export const screenFunds = (data) => http.post('/tool/fund-screener', data)

// 汇率换算
export const exchangeRate = (from, to, amount) => http.get('/tool/exchange-rate', { params: { from, to, amount } })
