import { describe, expect, it } from 'vitest'
import { api, json } from './helpers.js'

describe('health', () => {
  it('returns the ok envelope', async () => {
    const res = await api('/api/health')
    expect(res.status).toBe(200)
    expect(await json(res)).toEqual({ success: true, data: { ok: true } })
  })

  it('requires sign-in for private routes', async () => {
    const res = await api('/api/nope')
    expect(res.status).toBe(401)
    expect((await json(res)).success).toBe(false)
  })
})
