import { describe, expect, it } from 'vitest'
import { HOP, SAMPLE_RATE, bestMatch, landmarks, pcm16ToFloat } from '#helpers/fingerprint.js'
import { melody, withNoise } from './audio-signals.js'

describe('fingerprint', () => {
  const song = melody(20, 1)
  const stored = landmarks(song).map((l) => ({ ...l, tid: 7 }))
  const startFrame = 156
  const excerpt = song.slice(startFrame * HOP, startFrame * HOP + 6 * SAMPLE_RATE)

  it('produces a sane landmark density', () => {
    const perSecond = stored.length / 20
    expect(perSecond).toBeGreaterThan(10)
    expect(perSecond).toBeLessThan(400)
  })

  it('finds the track and offset in a noisy excerpt', () => {
    const match = bestMatch(landmarks(withNoise(excerpt, 0.05, 9)), stored)
    expect(match?.tid).toBe(7)
    expect(match?.offsetMs).toBe(startFrame * 32)
  })

  it('rejects an unrelated melody', () => {
    expect(bestMatch(landmarks(melody(6, 2)), stored)).toBeNull()
  })

  it('rejects pure noise', () => {
    expect(bestMatch(landmarks(withNoise(new Float32Array(6 * SAMPLE_RATE), 0.3, 3)), stored)).toBeNull()
  })

  it('decodes little-endian 16-bit PCM', () => {
    expect(Array.from(pcm16ToFloat(new Uint8Array([0x00, 0x40, 0x00, 0xc0])))).toEqual([0.5, -0.5])
  })
})
