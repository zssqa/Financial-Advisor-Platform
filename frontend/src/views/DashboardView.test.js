import { describe, it, expect } from 'vitest'

/**
 * 从 DashboardView.vue 提取的权重归一化逻辑（weightsAsPercent computed 的核心）。
 * 兼容小数 0-1（sum <= 1.5 时乘 100）与已是百分比 0-100（sum > 1.5 时保持不变）两种形态。
 */
function weightsAsPercent(arr) {
    const sum = arr.reduce((a, b) => a + Number(b.weight), 0)
    if (sum <= 0) return []
    const multiplier = sum <= 1.5 ? 100 : 1
    return arr.map(item => ({
        ...item,
        percent: Number((Number(item.weight) * multiplier).toFixed(2))
    }))
}

describe('DashboardView weightsAsPercent normalization', () => {
    it('weights as 0-1 decimals (sum <= 1.5): multiply by 100', () => {
        const input = [
            { type: 'stock', weight: 0.3 },
            { type: 'fund', weight: 0.7 }
        ]
        const result = weightsAsPercent(input)
        expect(result[0].percent).toBe(30)
        expect(result[1].percent).toBe(70)
    })

    it('weights as 0-100 percentages (sum > 1.5): keep as-is', () => {
        const input = [
            { type: 'stock', weight: 30 },
            { type: 'fund', weight: 70 }
        ]
        const result = weightsAsPercent(input)
        expect(result[0].percent).toBe(30)
        expect(result[1].percent).toBe(70)
    })

    it('empty weights: returns empty array', () => {
        expect(weightsAsPercent([])).toEqual([])
    })
})
