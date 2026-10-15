import { describe, expect, it } from 'vitest'
import { allowedQuality } from '#helpers/quality-gate.js'
import { tierFor } from '#helpers/entitlement.js'
import { asset } from './fixtures.js'

const full = { free_full_length: false, assets: { hires: asset('hires'), lossless: asset('lossless'), high: asset('high'), low: asset('low'), preview: asset('preview') } }
const cdOnlyFree = { free_full_length: true, assets: { lossless: asset('lossless'), high: asset('high'), low: asset('low'), preview: asset('preview') } }

describe('allowedQuality', () => {
  it.each([
    ['hires', 'premium', full, 'hires'],
    ['hires', 'premium', cdOnlyFree, 'lossless'],
    ['high', 'premium', full, 'high'],
    ['preview', 'premium', full, 'preview'],
    ['hires', 'free', full, 'preview'],
    ['hires', 'free', cdOnlyFree, 'low'],
  ] as const)('%s for %s', (requested, tier, track, expected) => {
    expect(allowedQuality(requested, tier, track)).toBe(expected)
  })

  it('returns null when nothing playable exists', () => {
    expect(allowedQuality('hires', 'free', { free_full_length: false, assets: {} })).toBeNull()
  })
})

describe('tierFor', () => {
  const now = new Date('2026-11-01T00:00:00Z')
  const future = new Date('2026-12-01T00:00:00Z')
  const past = new Date('2026-10-01T00:00:00Z')

  it.each([
    ['SUBSCRIPTION_STATE_ACTIVE', future, 'premium'],
    ['SUBSCRIPTION_STATE_IN_GRACE_PERIOD', future, 'premium'],
    ['SUBSCRIPTION_STATE_CANCELED', future, 'premium'],
    ['SUBSCRIPTION_STATE_CANCELED', past, 'free'],
    ['SUBSCRIPTION_STATE_ON_HOLD', future, 'free'],
    ['SUBSCRIPTION_STATE_EXPIRED', future, 'free'],
  ])('%s expiring %s', (state, expiresAt, tier) => {
    expect(tierFor({ state, expires_at: expiresAt }, now)).toBe(tier)
  })

  it('treats no subscription as free', () => {
    expect(tierFor(null, now)).toBe('free')
  })
})
