import { randomUUID } from 'node:crypto'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { coplayNeighbours } from '#helpers/coplay.js'
import { getDatabase } from '#helpers/mongoclient.js'
import { draftDailyMixes, matchPercent, scoreCandidate, seededShuffle, type Candidate } from '#helpers/recs.js'
import { foldGenres } from '#helpers/stats.js'
import { cosine, meanVector } from '#helpers/vector.js'
import { findNearestTrackIds } from '#helpers/vector-search.js'
import { api, json, newUserToken, wipeDb } from './helpers.js'
import { trackDoc } from './fixtures.js'

vi.mock('#helpers/vector-search.js', () => ({ findNearestTrackIds: vi.fn() }))
const nearest = vi.mocked(findNearestTrackIds)

describe('vector and ranking rules', () => {
  it('computes cosine and weighted means', () => {
    expect(cosine([1, 2, 3], [1, 2, 3])).toBeCloseTo(1)
    expect(cosine([1, 0], [0, 1])).toBeCloseTo(0)
    expect(meanVector([[0, 2], [2, 0]], [1, 3])).toEqual([1.5, 0.5])
    expect(meanVector([], [])).toBeNull()
  })

  it('maps cosine to a 50 to 99 match percentage', () => {
    expect(matchPercent(1)).toBe(99)
    expect(matchPercent(0)).toBe(50)
    expect(matchPercent(-1)).toBe(50)
    expect(matchPercent(0.92)).toBe(96)
  })

  it('sinks recently heard tracks', () => {
    const base: Candidate = { trackId: 't', genre: 'pop', cosine: 0.9, coplay: 0.5, isFresh: true, wasHeardRecently: false }
    expect(scoreCandidate({ ...base, wasHeardRecently: true })).toBeLessThan(scoreCandidate(base))
  })

  it('shuffles the same way for the same seed only', () => {
    const items = Array.from({ length: 20 }, (_, i) => i)
    expect(seededShuffle(items, 'u1:2026-11-01')).toEqual(seededShuffle(items, 'u1:2026-11-01'))
    expect(seededShuffle(items, 'u1:2026-11-01')).not.toEqual(seededShuffle(items, 'u1:2026-11-02'))
    expect([...seededShuffle(items, 'x')].sort((a, b) => a - b)).toEqual(items)
  })

  it('builds mixes from the most represented genres', () => {
    const c = (trackId: string, genre: string): Candidate => ({ trackId, genre, cosine: 0, coplay: 0, isFresh: false, wasHeardRecently: false })
    const mixes = draftDailyMixes([c('a', 'pop'), c('b', 'lo-fi'), c('c', 'pop'), c('d', 'jazz'), c('e', 'pop'), c('f', 'lo-fi'), c('g', 'rock')], 'seed')
    expect(mixes.map((m) => m.genre)).toEqual(['pop', 'lo-fi', 'jazz'])
    expect([...mixes[0]!.trackIds].sort()).toEqual(['a', 'c', 'e'])
  })

  it('folds genres after the top three into Others', () => {
    expect(foldGenres([{ _id: 'a', ms: 5 }, { _id: 'b', ms: 4 }, { _id: 'c', ms: 3 }, { _id: 'd', ms: 2 }, { _id: 'Others', ms: 1 }]))
      .toEqual([{ genre: 'a', ms: 5 }, { genre: 'b', ms: 4 }, { genre: 'c', ms: 3 }, { genre: 'Others', ms: 3 }])
  })

  it('scores co-play neighbours by shared listeners', () => {
    const neighbours = coplayNeighbours(new Map([
      ['A', new Set(['t1', 't2'])],
      ['B', new Set(['t1', 't2'])],
      ['C', new Set(['t1', 't3'])],
    ]))
    const t1 = neighbours.get('t1')!
    expect(t1[0]).toEqual({ track_id: 't2', score: 2 / Math.sqrt(3 * 2) })
    expect(t1[1]!.score).toBeCloseTo(1 / Math.sqrt(3))
  })
})

describe('discover and home', () => {
  beforeEach(async () => {
    await wipeDb()
    vi.clearAllMocks()
    const db = await getDatabase()
    await db.collection('catalog_artists').insertOne({ _id: 'artist-a' as never, name: 'A', slug: 'a', bio: '', rights_holder_id: 'rh', created_at: new Date() })
    await db.collection('catalog_albums').insertOne({ _id: 'album-a' as never, artist_id: 'artist-a', title: 'Album', year: 2026, upc: '', genres: [], created_at: new Date() })
    const lofi = [1, 0, 0]
    const pop = [0, 1, 0]
    await db.collection('catalog_tracks').insertMany([
      trackDoc({ _id: 'lofi-played', title: 'Lofi Played', album_id: 'album-a', artist_id: 'artist-a', embedding: lofi, genres: ['lo-fi'] }),
      trackDoc({ _id: 'lofi-new', title: 'Lofi New', album_id: 'album-a', artist_id: 'artist-a', embedding: [0.95, 0.05, 0], genres: ['lo-fi'] }),
      trackDoc({ _id: 'pop-new', title: 'Pop New', album_id: 'album-a', artist_id: 'artist-a', embedding: pop, genres: ['pop'] }),
    ] as never[])
    nearest.mockResolvedValue(['lofi-new', 'pop-new', 'lofi-played'])
  })

  it('ranks the closest unheard track first and a recently heard one below it', async () => {
    const { token } = await newUserToken()
    await api('/api/plays', { method: 'POST', body: JSON.stringify({ events: [{ client_event_id: randomUUID(), track_id: 'lofi-played', ms_played: 200_000, started_at: new Date().toISOString(), quality: 'hires', offline: false }] }) }, token)
    const tracks = (await json(await api('/api/discover', {}, token))).data.tracks
    expect(tracks.map((t: { id: string }) => t.id)).toEqual(['lofi-new', 'lofi-played', 'pop-new'])
    expect(tracks[0].match_percent).toBeGreaterThan(tracks[2].match_percent)
  })

  it('uses onboarding artists when there is no history', async () => {
    const { token } = await newUserToken({ taste_artist_ids: ['artist-a'] })
    const tracks = (await json(await api('/api/discover', {}, token))).data.tracks
    expect(nearest).toHaveBeenCalled()
    expect(tracks).toHaveLength(3)
  })

  it('returns home with mixes, discover preview and catalog genres', async () => {
    const { token } = await newUserToken({ taste_artist_ids: ['artist-a'] })
    const home = (await json(await api('/api/home', {}, token))).data
    expect(home.daily_mixes.map((m: { subtitle: string }) => m.subtitle).sort()).toEqual(['lo-fi', 'pop'])
    expect(home.discover_preview.count).toBe(3)
    expect(home.genres).toEqual(['lo-fi', 'pop'])
    expect(home.featured).toHaveLength(3)
  })
})
