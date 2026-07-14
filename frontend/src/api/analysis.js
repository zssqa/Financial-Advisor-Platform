import http from './http.js'

// 获取K线图（后端返回图片路径或 base64）
export const getKline = (symbol) => http.get('/analysis/kline', { params: { symbol } }).then(res => res.data.data)

// 获取组合优化配置（返回最优权重及相关指标）
export const getOptimize = () => http.get('/analysis/optimize').then(res => res.data.data)

// 获取风险收益散点数据（返回 [{ name, annualReturn, annualVolatility, marketValue }]）
export const getRiskReturn = () => http.get('/analysis/risk-return').then(res => res.data.data)
