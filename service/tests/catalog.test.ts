import { beforeEach, describe, expect, it } from 'vitest'
import { api, json, newUserToken, wipeDb } from './helpers.js'
import { seedCatalog } from './fixtures.js'

describe('catalog', () => {
  let token: string

  beforeEach(async () => {
    await wipeDb()
    await seedCatalog()
    token = (await newUserToken()).token
  })

  it('returns an album with tracks in order and no storage keys', async () => {
    const res = await api('/api/albums/album-night', {}, token)
    const body = await json(res)
    expect(res.status).toBe(200)
    expect(body.data.tracks.map((t: { id: string }) => t.id)).toEqual(['track-neon', 'track-midnight'])
    expect(body.data.cover_url).toBe('/api/images/albums/album-night')
    const raw = JSON.stringify(body)
    expect(raw).not.toContain('audio/t/')
    expect(raw).not.toContain('embedding')
  })

  it('reports the best available quality per track', async () => {
    const body = await json(await api('/api/tracks/track-cd', {}, token))
    expect(body.data.best_quality).toEqual({ quality: 'lossless', codec: 'flac', sample_rate: 44100, bit_depth: 16 })
  })

  it('returns an artist with albums and top tracks', async () => {
    const body = await json(await api('/api/artists/artist-mira', {}, token))
    expect(body.data.name).toBe('Mira Sen')
    expect(body.data.albums).toHaveLength(1)
    expect(body.data.top_tracks).toHaveLength(2)
  })

  it('searches tracks, albums and artists', async () => {
    const body = await json(await api('/api/search?q=midnight', {}, token))
    expect(body.data.tracks.map((t: { title: string }) => t.title)).toEqual(['Midnight Drive'])
    const byArtist = await json(await api('/api/search?q=mira', {}, token))
    expect(byArtist.data.artists[0].id).toBe('artist-mira')
  })

  it('rejects an empty search', async () => {
    expect((await api('/api/search?q=', {}, token)).status).toBe(400)
  })

  it('returns 404 for an unknown track', async () => {
    expect((await api('/api/tracks/missing', {}, token)).status).toBe(404)
  })
})
