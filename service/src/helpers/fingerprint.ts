// Landmark audio fingerprinting (Wang 2003): spectrogram peaks are paired into
// hashes of (f1, f2, dt); a recording matches when many hashes agree on one
// time offset. Dependency-free so it runs the same in the ingest CLI and the API.

export const SAMPLE_RATE = 16000
export const HOP = 512
const FRAME = 1024
const BINS = FRAME / 2
const TIME_RADIUS = 10
const FREQUENCY_RADIUS = 20
const PEAK_MARGIN_DB = 10
const FAN_OUT = 5
const MAX_DT = 63
const MIN_VOTES = 8
const MIN_VOTE_RATIO = 0.03
const FRAME_MS = (HOP / SAMPLE_RATE) * 1000

export interface Landmark {
  h: number
  t: number
}

export interface StoredLandmark extends Landmark {
  tid: number
}

export interface FingerprintMatch {
  tid: number
  offsetMs: number
  votes: number
  confidence: number
}

interface Peak {
  t: number
  f: number
}

export function pcm16ToFloat(pcm: Uint8Array): Float32Array {
  const view = new DataView(pcm.buffer, pcm.byteOffset, pcm.byteLength)
  const out = new Float32Array(pcm.byteLength >> 1)
  for (let i = 0; i < out.length; i++) out[i] = view.getInt16(i * 2, true) / 32768
  return out
}

export function landmarks(samples: Float32Array): Landmark[] {
  return pairPeaks(findPeaks(spectrogramDb(samples)))
}

export function bestMatch(query: Landmark[], stored: StoredLandmark[]): FingerprintMatch | null {
  const queryTimesByHash = new Map<number, number[]>()
  for (const q of query) {
    const times = queryTimesByHash.get(q.h)
    if (times) times.push(q.t)
    else queryTimesByHash.set(q.h, [q.t])
  }

  const votesByOffset = new Map<string, number>()
  let best = { key: '', votes: 0 }
  for (const s of stored) {
    for (const queryTime of queryTimesByHash.get(s.h) ?? []) {
      const key = `${s.tid}:${s.t - queryTime}`
      const votes = (votesByOffset.get(key) ?? 0) + 1
      votesByOffset.set(key, votes)
      if (votes > best.votes) best = { key, votes }
    }
  }

  if (best.votes < MIN_VOTES || best.votes < MIN_VOTE_RATIO * query.length) return null
  const [tid = 0, offsetFrames = 0] = best.key.split(':').map(Number)
  return { tid, offsetMs: offsetFrames * FRAME_MS, votes: best.votes, confidence: best.votes / query.length }
}

function spectrogramDb(samples: Float32Array): Float32Array[] {
  const hann = Float64Array.from({ length: FRAME }, (_, i) => 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / (FRAME - 1)))
  const re = new Float64Array(FRAME)
  const im = new Float64Array(FRAME)
  const frames: Float32Array[] = []
  for (let start = 0; start + FRAME <= samples.length; start += HOP) {
    for (let i = 0; i < FRAME; i++) {
      re[i] = samples[start + i]! * hann[i]!
      im[i] = 0
    }
    fft(re, im)
    const db = new Float32Array(BINS)
    for (let k = 0; k < BINS; k++) db[k] = 10 * Math.log10(re[k]! * re[k]! + im[k]! * im[k]! + 1e-12)
    frames.push(db)
  }
  return frames
}

// In-place iterative radix-2 FFT; `re.length` must be a power of two.
function fft(re: Float64Array, im: Float64Array): void {
  const n = re.length
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1
    for (; j & bit; bit >>= 1) j ^= bit
    j ^= bit
    if (i < j) {
      ;[re[i], re[j]] = [re[j]!, re[i]!]
      ;[im[i], im[j]] = [im[j]!, im[i]!]
    }
  }
  for (let length = 2; length <= n; length <<= 1) {
    const angle = (-2 * Math.PI) / length
    const stepRe = Math.cos(angle)
    const stepIm = Math.sin(angle)
    for (let i = 0; i < n; i += length) {
      let wRe = 1
      let wIm = 0
      for (let k = 0; k < length / 2; k++) {
        const a = i + k
        const b = a + length / 2
        const tRe = re[b]! * wRe - im[b]! * wIm
        const tIm = re[b]! * wIm + im[b]! * wRe
        re[b] = re[a]! - tRe
        im[b] = im[a]! - tIm
        re[a] = re[a]! + tRe
        im[a] = im[a]! + tIm
        const nextRe = wRe * stepRe - wIm * stepIm
        wIm = wRe * stepIm + wIm * stepRe
        wRe = nextRe
      }
    }
  }
}

function slidingMax(row: Float32Array, radius: number): Float32Array {
  const out = new Float32Array(row.length)
  for (let i = 0; i < row.length; i++) {
    let max = -Infinity
    const end = Math.min(row.length - 1, i + radius)
    for (let j = Math.max(0, i - radius); j <= end; j++) max = Math.max(max, row[j]!)
    out[i] = max
  }
  return out
}

// A peak is the loudest point in its time x frequency neighbourhood and clearly
// above the clip's average level. Output is sorted by time, then frequency.
function findPeaks(spectrogram: Float32Array[]): Peak[] {
  if (spectrogram.length === 0) return []
  let sum = 0
  for (const frame of spectrogram) for (const v of frame) sum += v
  const floor = sum / (spectrogram.length * BINS) + PEAK_MARGIN_DB
  const frequencyMax = spectrogram.map((frame) => slidingMax(frame, FREQUENCY_RADIUS))

  const peaks: Peak[] = []
  for (let t = 0; t < spectrogram.length; t++) {
    const from = Math.max(0, t - TIME_RADIUS)
    const to = Math.min(spectrogram.length - 1, t + TIME_RADIUS)
    const frame = spectrogram[t]!
    for (let f = 1; f < BINS; f++) {
      const value = frame[f]!
      if (value < floor || value < frequencyMax[t]![f]!) continue
      let isLoudest = true
      for (let u = from; u <= to && isLoudest; u++) if (frequencyMax[u]![f]! > value) isLoudest = false
      if (isLoudest) peaks.push({ t, f })
    }
  }
  return peaks
}

function pairPeaks(peaks: Peak[]): Landmark[] {
  const out: Landmark[] = []
  for (let i = 0; i < peaks.length; i++) {
    const anchor = peaks[i]!
    let paired = 0
    for (let j = i + 1; j < peaks.length && paired < FAN_OUT; j++) {
      const target = peaks[j]!
      const dt = target.t - anchor.t
      if (dt === 0) continue
      if (dt > MAX_DT) break
      // f < 512, so f1 << 16 stays below 2^31.
      out.push({ h: (anchor.f << 16) | (target.f << 6) | dt, t: anchor.t })
      paired++
    }
  }
  return out
}
