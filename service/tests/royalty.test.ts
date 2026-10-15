import { describe, expect, it } from 'vitest'
import { splitRoyalties } from '#helpers/royalty.js'

describe('splitRoyalties', () => {
  it('splits pro-rata by premium listening time', () => {
    expect(splitRoyalties(1_000_000n, [
      { id: 'a', payoutShare: 0.7, premiumMs: 3_000 },
      { id: 'b', payoutShare: 0.7, premiumMs: 1_000 },
    ])).toEqual([{ id: 'a', payoutPaise: 525_000n }, { id: 'b', payoutPaise: 175_000n }])
  })

  it('never pays out more than the pool when shares do not divide evenly', () => {
    const payouts = splitRoyalties(1_000n, [1, 1, 1].map((ms, i) => ({ id: String(i), payoutShare: 1, premiumMs: ms })))
    expect(payouts.reduce((sum, p) => sum + p.payoutPaise, 0n)).toBeLessThanOrEqual(1_000n)
  })

  it('pays nothing when nobody listened', () => {
    expect(splitRoyalties(1_000n, [{ id: 'a', payoutShare: 0.7, premiumMs: 0 }])).toEqual([{ id: 'a', payoutPaise: 0n }])
  })
})
