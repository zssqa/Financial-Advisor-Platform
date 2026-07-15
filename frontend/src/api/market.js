import http from './http.js'

// 四大指数行情（上证指数、深证成指、创业板指、科创50）
export function getMarketIndices() {
    return http.get('/markets/indices').then(res => res.data.data)
}

// 市场贪婪/恐惧情绪指数
export function getMarketSentiment() {
    return http.get('/markets/sentiment').then(res => res.data.data)
}

// 本周金融日历（经济数据发布日程）
export function getMarketCalendar() {
    return http.get('/markets/calendar').then(res => res.data.data)
}
