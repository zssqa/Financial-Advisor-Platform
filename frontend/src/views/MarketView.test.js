import { describe, it, expect } from 'vitest'

/**
 * 以下函数从 MarketView.vue 提取，用于测试字段别名、-100 哨兵值与情绪分桶逻辑。
 */

// 四大指数价格字段适配（兼容后端不同命名）
function indexPrice(i) {
    const raw = i.price ?? i.point ?? i.value ?? i.close ?? i.last ?? null
    if (raw === null || raw === undefined) return null
    const v = Number(raw)
    if (isNaN(v) || v === 0) return null
    return v
}

// 涨跌幅字段适配，-100 视为无效哨兵值
function indexChange(i) {
    const raw = i.changePercent ?? i.pctChange ?? i.percent ?? i.changePct ?? i.change ?? null
    if (raw === null || raw === undefined) return null
    const v = Number(raw)
    if (isNaN(v) || v === -100) return null
    return v
}

// 市场情绪分桶（贪婪/恐惧指数 0-100）
function sentimentLabel(v) {
    if (v <= 24) return '极度恐惧'
    if (v <= 44) return '恐惧'
    if (v <= 55) return '中性'
    if (v <= 75) return '贪婪'
    return '极度贪婪'
}

describe('MarketView indexPrice field aliasing', () => {
    it('reads price field', () => {
        expect(indexPrice({ price: 3200.5 })).toBe(3200.5)
    })

    it('falls back to point field', () => {
        expect(indexPrice({ point: 2800 })).toBe(2800)
    })

    it('falls back to value / close / last', () => {
        expect(indexPrice({ value: 1500 })).toBe(1500)
        expect(indexPrice({ close: 99.5 })).toBe(99.5)
        expect(indexPrice({ last: 42 })).toBe(42)
    })

    it('returns null when no field present', () => {
        expect(indexPrice({})).toBe(null)
    })

    it('returns null when value is 0 (invalid)', () => {
        expect(indexPrice({ price: 0 })).toBe(null)
    })
})

describe('MarketView indexChange -100 sentinel', () => {
    it('reads changePercent field', () => {
        expect(indexChange({ changePercent: 1.5 })).toBe(1.5)
    })

    it('falls back to pctChange / percent / changePct / change', () => {
        expect(indexChange({ pctChange: -0.5 })).toBe(-0.5)
        expect(indexChange({ percent: 2.3 })).toBe(2.3)
        expect(indexChange({ changePct: 0 })).toBe(0)
        expect(indexChange({ change: -1 })).toBe(-1)
    })

    it('returns null for -100 sentinel value', () => {
        expect(indexChange({ changePercent: -100 })).toBe(null)
    })

    it('returns null when no field present', () => {
        expect(indexChange({})).toBe(null)
    })
})

describe('MarketView sentiment bucketing', () => {
    it('0 → 极度恐惧', () => {
        expect(sentimentLabel(0)).toBe('极度恐惧')
    })

    it('25 → 恐惧', () => {
        expect(sentimentLabel(25)).toBe('恐惧')
    })

    it('45 → 中性', () => {
        expect(sentimentLabel(45)).toBe('中性')
    })

    it('56 → 贪婪', () => {
        expect(sentimentLabel(56)).toBe('贪婪')
    })

    it('76 → 极度贪婪', () => {
        expect(sentimentLabel(76)).toBe('极度贪婪')
    })
})
