import { randomUUID } from 'node:crypto'
import { beforeEach, describe, expect, it } from 'vitest'
import { getDatabase } from '#helpers/mongoclient.js'
import { api, json, newUserToken, wipeDb } from './helpers.js'
import { seedCatalog } from './fixtures.js'

const post = (body: object) => ({ method: 'POST', body: JSON.stringify(body) })
const play = (trackId: string, msPlayed: number, startedAt: Date) => ({ client_event_id: randomUUID(), track_id: trackId, ms_played: msPlayed, started_at: startedAt.toISOString(), quality: 'hires', offline: false })

describe('library', () => {
  let token: string
  beforeEach(async () => {
    await wipeDb()
    await seedCatalog()
    token = (await newUserToken()).token
  })

  it('saves, lists once, and removes a track', async () => {
    await api('/api/library', post({ kind: 'track', item_id: 'track-midnight' }), token)
    await api('/api/library', post({ kind: 'track', item_id: 'track-midnight' }), token)
    const listed = await json(await api('/api/library?kind=track', {}, token))
    expect(listed.data.items.map((t: { id: string }) => t.id)).toEqual(['track-midnight'])
    await api('/api/library/track/track-midnight', { method: 'DELETE' }, token)
    expect((await json(await api('/api/library?kind=track', {}, token))).data.items).toEqual([])
  })

  it('lists saved albums', async () => {
    await api('/api/library', post({ kind: 'album', item_id: 'album-glass' }), token)
    const listed = await json(await api('/api/library?kind=album', {}, token))
    expect(listed.data.items[0]).toMatchObject({ id: 'album-glass', title: 'Glass' })
  })

  it('rejects an unknown kind', async () => {
    expect((await api('/api/library?kind=podcast', {}, token)).status).toBe(400)
  })
})

describe('plays', () => {
  let token: string
  beforeEach(async () => {
    await wipeDb()
    await seedCatalog()
    token = (await newUserToken()).token
  })

  it('stores each event once even when the batch is resent', async () => {
    const batch = { events: [play('track-midnight', 60_000, new Date())] }
    await api('/api/plays', post(batch), token)
    const again = await json(await api('/api/plays', post(batch), token))
    expect(again.data.stored).toBe(0)
    const db = await getDatabase()
    expect(await db.collection('play_events').countDocuments()).toBe(1)
  })

  it('enriches events with artist, genres and tier', async () => {
    await api('/api/plays', post({ events: [play('track-rooftops', 60_000, new Date())] }), token)
    const db = await getDatabase()
    expect(await db.collection('play_events').findOne()).toMatchObject({ artist_id: 'artist-mira', genres: ['pop'], tier: 'free' })
  })

  it('rejects listening time longer than the track', async () => {
    expect((await api('/api/plays', post({ events: [play('track-midnight', 600_000, new Date())] }), token)).status).toBe(400)
  })

  it('rejects events from the future', async () => {
    expect((await api('/api/plays', post({ events: [play('track-midnight', 60_000, new Date(Date.now() + 3_600_000))] }), token)).status).toBe(400)
  })

  it('skips tracks that no longer exist', async () => {
    const res = await json(await api('/api/plays', post({ events: [play('gone', 60_000, new Date())] }), token))
    expect(res.data.stored).toBe(0)
  })
})

describe('stats', () => {
  beforeEach(async () => {
    await wipeDb()
    await seedCatalog()
  })

  it('sums listening time, peak hour, top artists and genres for the period', async () => {
    const { token } = await newUserToken()
    const other = await newUserToken()
    // 16:30 UTC is 22:00 in India.
    const tenPmIndia = new Date()
    tenPmIndia.setUTCHours(16, 30, 0, 0)
    if (tenPmIndia > new Date()) tenPmIndia.setUTCDate(tenPmIndia.getUTCDate() - 1)
    await api('/api/plays', post({ events: [
      play('track-midnight', 200_000, tenPmIndia),
      play('track-neon', 100_000, tenPmIndia),
      play('track-rooftops', 50_000, new Date(tenPmIndia.getTime() - 3 * 3_600_000)),
    ] }), token)
    await api('/api/plays', post({ events: [play('track-rooftops', 200_000, tenPmIndia)] }), other.token)

    const body = (await json(await api('/api/stats?period=7d', {}, token))).data
    expect(body.total_ms).toBe(350_000)
    expect(body.songs_played).toBe(3)
    expect(body.peak_hour).toBe(22)
    expect(body.top_artists.map((a: { name: string }) => a.name)).toEqual(['Arjun Rao', 'Mira Sen'])
    expect(body.genres).toEqual([{ genre: 'lo-fi', ms: 300_000 }, { genre: 'pop', ms: 50_000 }])
  })

  it('returns zeros when nothing was played', async () => {
    const { token } = await newUserToken()
    const body = (await json(await api('/api/stats', {}, token))).data
    expect(body).toMatchObject({ total_ms: 0, songs_played: 0, peak_hour: null, top_artists: [], genres: [] })
  })
})
