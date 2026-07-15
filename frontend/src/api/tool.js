import http from './http.js'

// 基金筛选
export const screenFunds = (data) => http.post('/funds:screen', data).then(res => res.data.data)

// 汇率换算
export const exchangeRate = (from, to, amount) => http.get('/exchange-rates', { params: { from, to, amount } }).then(res => res.data.data)
