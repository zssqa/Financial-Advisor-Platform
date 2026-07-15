import http from './http.js'

// 获取组合优化配置（返回最优权重及相关指标）
export const getOptimize = () => http.get('/portfolios/optimization').then(res => res.data.data)

// 获取风险收益散点数据（返回 [{ name, annualReturn, annualVolatility, marketValue }]）
export const getRiskReturn = () => http.get('/portfolios/risk-return').then(res => res.data.data)
