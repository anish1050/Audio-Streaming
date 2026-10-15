import { randomUUID } from 'node:crypto'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { recognizeExternally, wavFromPcm16 } from '#helpers/audd.js'
import { HOP, SAMPLE_RATE, landmarks } from '#helpers/fingerprint.js'
import { getDatabase } from '#helpers/mongoclient.js'
import { api, json, newUserToken, wipeDb } from './helpers.js'
import { melody, withNoise } from './audio-signals.js'
import { seedCatalog } from './fixtures.js'

function toPcm16(samples: Float32Array): Uint8Array<ArrayBuffer> {
  const out = new DataView(new ArrayBuffer(samples.length * 2))
  samples.forEach((s, i) => out.setInt16(i * 2, Math.max(-32768, Math.min(32767, Math.round(s * 32767))), true))
  return new Uint8Array(out.buffer)
}

const recognizePost = (clip: Uint8Array<ArrayBuffer>) => ({ method: 'POST', body: clip, headers: { 'Content-Type': 'application/octet-stream' } })

describe('recognize', () => {
  const song = melody(20, 1)

  beforeEach(async () => {
    await wipeDb()
    await seedCatalog()
    const db = await getDatabase()
    await db.collection('catalog_tracks').updateOne({ _id: 'track-midnight' as never }, { $set: { fp_id: 7 } })
    await db.collection('fingerprints').insertMany(landmarks(song).map((l) => ({ ...l, tid: 7 })))
  })

  it('identifies a catalog track and where in it the clip starts', async () => {
    const { token } = await newUserToken()
    const clip = toPcm16(withNoise(song.slice(156 * HOP, 156 * HOP + 6 * SAMPLE_RATE), 0.05, 4))
    const body = (await json(await api('/api/recognize', recognizePost(clip), token))).data
    expect(body.match.track.id).toBe('track-midnight')
    expect(body.match.offset_ms).toBe(156 * 32)
  })

  it('returns no match for unknown music when the external fallback is off', async () => {
    const { token } = await newUserToken()
    const body = (await json(await api('/api/recognize', recognizePost(toPcm16(melody(6, 2))), token))).data
    expect(body).toEqual({ match: null, external: null })
  })

  it('rejects clips that are too long or too short', async () => {
    const { token } = await newUserToken()
    expect((await api('/api/recognize', recognizePost(new Uint8Array(400_000)), token)).status).toBe(413)
    expect((await api('/api/recognize', recognizePost(new Uint8Array(1000)), token)).status).toBe(400)
  })

  it('records a catalog request', async () => {
    const { token } = await newUserToken()
    const res = await api('/api/recognize/request', { method: 'POST', body: JSON.stringify({ title: 'Some Song', artist: 'Someone' }) }, token)
    expect(res.status).toBe(201)
  })
})

describe('external recognition cap', () => {
  beforeEach(async () => {
    await wipeDb()
    process.env.AUDD_ENABLED = 'true'
    process.env.AUDD_API_TOKEN = 'test'
    process.env.AUDD_MONTHLY_CAP = '2'
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ status: 'success', result: { title: 'Hit', artist: 'Star' } }))))
  })

  afterEach(() => {
    process.env.AUDD_ENABLED = 'false'
    vi.unstubAllGlobals()
  })

  it('stops calling AudD once the monthly cap is used', async () => {
    const now = new Date('2026-11-05T00:00:00Z')
    const clip = new Uint8Array(64_000)
    expect(await recognizeExternally(clip, now)).toEqual({ title: 'Hit', artist: 'Star' })
    expect(await recognizeExternally(clip, now)).not.toBeNull()
    expect(await recognizeExternally(clip, now)).toBeNull()
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('builds a valid WAV header', () => {
    const wav = wavFromPcm16(new Uint8Array(32_000))
    expect(new TextDecoder().decode(wav.slice(0, 4))).toBe('RIFF')
    expect(new DataView(wav.buffer).getUint32(24, true)).toBe(16000)
    expect(wav.byteLength).toBe(32_044)
  })
})

describe('co-play job', () => {
  beforeEach(async () => {
    await wipeDb()
    await seedCatalog()
  })

  it('requires the cron secret', async () => {
    expect((await api('/api/jobs/coplay', { method: 'POST' })).status).toBe(401)
  })

  it('writes neighbours from real listens only', async () => {
    const listen = (trackId: string, ms: number) => ({ client_event_id: randomUUID(), track_id: trackId, ms_played: ms, started_at: new Date().toISOString(), quality: 'hires', offline: false })
    for (let i = 0; i < 2; i++) {
      const { token } = await newUserToken()
      await api('/api/plays', { method: 'POST', body: JSON.stringify({ events: [listen('track-midnight', 60_000), listen('track-neon', 60_000), listen('track-rooftops', 10_000)] }) }, token)
    }
    const res = await api('/api/jobs/coplay', { method: 'POST', headers: { Authorization: 'Bearer cron-test-secret' } })
    expect((await json(res)).data.tracks).toBe(2)
    const db = await getDatabase()
    const neighbours = await db.collection('track_neighbors').findOne({ _id: 'track-midnight' as never })
    expect(neighbours?.coplay).toEqual([{ track_id: 'track-neon', score: 1 }])
  })
})

describe('credits', () => {
  it('lists Creative Commons tracks publicly', async () => {
    await wipeDb()
    await seedCatalog()
    const db = await getDatabase()
    await db.collection('catalog_tracks').updateOne({ _id: 'track-neon' as never }, { $set: { licence: 'CC-BY-4.0', attribution: 'Arjun Rao, CC BY 4.0' } })
    const credits = (await json(await api('/api/credits'))).data.credits
    expect(credits).toEqual([{ track_id: 'track-neon', title: 'Neon Rain', artist: 'Arjun Rao', licence: 'CC-BY-4.0', attribution: 'Arjun Rao, CC BY 4.0' }])
  })
})
