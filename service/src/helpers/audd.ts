import { getDatabase, isDuplicateKeyError } from './mongoclient.js'
import type { CounterDoc } from '#types/docs.js'

export interface ExternalMatch {
  title: string
  artist: string
}

/**
 * Recognises songs outside our catalog through AudD. Off unless AUDD_ENABLED
 * is "true", and never more than AUDD_MONTHLY_CAP paid lookups a month.
 * The clip is sent once and never stored.
 */
export async function recognizeExternally(pcm16Mono16k: Uint8Array, now: Date): Promise<ExternalMatch | null> {
  const token = process.env.AUDD_API_TOKEN
  const cap = Number(process.env.AUDD_MONTHLY_CAP ?? 0)
  if (process.env.AUDD_ENABLED !== 'true' || !token || cap <= 0) return null
  if (!(await claimMonthlyLookup(now, cap))) return null

  const form = new FormData()
  form.set('api_token', token)
  form.set('file', new Blob([wavFromPcm16(pcm16Mono16k)], { type: 'audio/wav' }), 'clip.wav')
  const res = await fetch('https://api.audd.io/', { method: 'POST', body: form })
  if (!res.ok) throw new Error(`[audd] ${res.status}`)
  const body = (await res.json()) as { status?: string; result?: { title?: string; artist?: string } | null }
  return body.status === 'success' && body.result?.title ? { title: body.result.title, artist: body.result.artist ?? '' } : null
}

/** Atomically counts a lookup; false once this month's cap is reached. */
async function claimMonthlyLookup(now: Date, cap: number): Promise<boolean> {
  const db = await getDatabase()
  const counterId = `audd:${now.toISOString().slice(0, 7)}`
  try {
    await db.collection<CounterDoc>('counters').findOneAndUpdate({ _id: counterId, seq: { $lt: cap } }, { $inc: { seq: 1 } }, { upsert: true })
    return true
  } catch (e) {
    // The upsert collides with the existing counter when it is already at the cap.
    if (isDuplicateKeyError(e)) return false
    throw e
  }
}

export function wavFromPcm16(pcm: Uint8Array, sampleRate = 16000): Uint8Array<ArrayBuffer> {
  const header = new DataView(new ArrayBuffer(44))
  const writeAscii = (offset: number, text: string) => [...text].forEach((ch, i) => header.setUint8(offset + i, ch.charCodeAt(0)))
  writeAscii(0, 'RIFF')
  header.setUint32(4, 36 + pcm.byteLength, true)
  writeAscii(8, 'WAVE')
  writeAscii(12, 'fmt ')
  header.setUint32(16, 16, true)
  header.setUint16(20, 1, true)
  header.setUint16(22, 1, true)
  header.setUint32(24, sampleRate, true)
  header.setUint32(28, sampleRate * 2, true)
  header.setUint16(32, 2, true)
  header.setUint16(34, 16, true)
  writeAscii(36, 'data')
  header.setUint32(40, pcm.byteLength, true)
  const wav = new Uint8Array(44 + pcm.byteLength)
  wav.set(new Uint8Array(header.buffer), 0)
  wav.set(pcm, 44)
  return wav
}
