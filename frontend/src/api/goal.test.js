import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock the http module BEFORE importing the API file
vi.mock('./http.js', () => {
    const http = {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn()
    }
    return { default: http }
})

import http from './http.js'
import { listGoals, createGoal, updateGoal, deleteGoal, getSummary } from './goal.js'

describe('goal.js', () => {
    beforeEach(() => {
        http.get.mockReset()
        http.post.mockReset()
        http.put.mockReset()
        http.delete.mockReset()
    })

    it('listGoals calls GET /goals and unwraps res.data.data', async () => {
        const payload = [{ id: 1, name: 'retire' }]
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await listGoals()

        expect(http.get).toHaveBeenCalledWith('/goals')
        expect(result).toBe(payload)
    })

    it('createGoal calls POST /goals with body=goal', async () => {
        const payload = { id: 2, name: 'house' }
        http.post.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })
        const goal = { name: 'house', targetAmount: 100000 }

        const result = await createGoal(goal)

        expect(http.post).toHaveBeenCalledWith('/goals', goal)
        expect(result).toBe(payload)
    })

    it('updateGoal calls PUT /goals/${id} with body=goal', async () => {
        const payload = { id: 3, name: 'car' }
        http.put.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })
        const goal = { name: 'car', targetAmount: 50000 }

        const result = await updateGoal(3, goal)

        expect(http.put).toHaveBeenCalledWith('/goals/3', goal)
        expect(result).toBe(payload)
    })

    it('deleteGoal calls DELETE /goals/${id}', async () => {
        const payload = { success: true }
        http.delete.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await deleteGoal(4)

        expect(http.delete).toHaveBeenCalledWith('/goals/4')
        expect(result).toBe(payload)
    })

    it('getSummary calls GET /goals/summary and unwraps res.data.data', async () => {
        const payload = { totalTarget: 150000, achieved: 40000 }
        http.get.mockResolvedValue({ data: { code: 200, message: 'ok', data: payload } })

        const result = await getSummary()

        expect(http.get).toHaveBeenCalledWith('/goals/summary')
        expect(result).toBe(payload)
    })
})
