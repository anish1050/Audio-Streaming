import { SAMPLE_RATE } from '#helpers/fingerprint.js'

function seededRandom(seed: number) {
  let state = seed
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0
    return state / 2 ** 32
  }
}

// Five notes a second, three decaying tones each: enough structure to behave like music.
export function melody(seconds: number, seed: number): Float32Array {
  const random = seededRandom(seed)
  const out = new Float32Array(seconds * SAMPLE_RATE)
  const noteLength = SAMPLE_RATE / 5
  for (let start = 0; start < out.length; start += noteLength) {
    const tones = [0, 1, 2].map(() => ({ frequency: 200 + random() * 3500, amplitude: 0.2 + random() * 0.3 }))
    for (let i = 0; i < noteLength && start + i < out.length; i++) {
      const envelope = Math.exp(-i / (0.08 * SAMPLE_RATE))
      out[start + i] = tones.reduce((sum, t) => sum + t.amplitude * envelope * Math.sin((2 * Math.PI * t.frequency * i) / SAMPLE_RATE), 0)
    }
  }
  return out
}

export function withNoise(samples: Float32Array, level: number, seed: number): Float32Array {
  const random = seededRandom(seed)
  return samples.map((v) => v + (random() * 2 - 1) * level)
}
