import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { albumRef, artistRef, loadTrackViews, loadTrackViewsWhere } from '#helpers/catalog-view.js'
import { MongoClientFind, MongoClientFindOne } from '#helpers/mongoclient.js'
import { errorResponse, successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { AuthVariables } from '#middleware/proxy.js'
import type { AlbumDoc, ArtistDoc, TrackDoc } from '#types/docs.js'

const SEARCH_LIMIT = 20
const searchSchema = z.object({ q: z.string().trim().min(1).max(80) }).strict()

export const catalogRoutes = new Hono<{ Variables: AuthVariables }>()

catalogRoutes.get('/tracks/:id', async (c) => {
  try {
    const [track] = await loadTrackViews([c.req.param('id')])
    return track ? successResponse(c, track) : errorResponse(c, 'Track not found', 404)
  } catch (e) {
    return handleError(c, 'catalog:track', e)
  }
})

catalogRoutes.get('/albums/:id', async (c) => {
  try {
    const { data: album } = await MongoClientFindOne<AlbumDoc>('catalog_albums', { _id: c.req.param('id') })
    if (!album) return errorResponse(c, 'Album not found', 404)
    const [{ data: artist }, tracks] = await Promise.all([
      MongoClientFindOne<ArtistDoc>('catalog_artists', { _id: album.artist_id }),
      loadTrackViewsWhere({ album_id: album._id }, { track_no: 1 }, 200),
    ])
    return successResponse(c, { ...albumRef(album), year: album.year, genres: album.genres, artist: artist && artistRef(artist), tracks })
  } catch (e) {
    return handleError(c, 'catalog:album', e)
  }
})

catalogRoutes.get('/artists/:id', async (c) => {
  try {
    const { data: artist } = await MongoClientFindOne<ArtistDoc>('catalog_artists', { _id: c.req.param('id') })
    if (!artist) return errorResponse(c, 'Artist not found', 404)
    const [{ data: albums }, topTracks] = await Promise.all([
      MongoClientFind<AlbumDoc>('catalog_albums', { artist_id: artist._id }, { sort: { year: -1 } }),
      // ponytail: newest first until play counts are aggregated per track.
      loadTrackViewsWhere({ artist_id: artist._id }, { created_at: -1 }, 10),
    ])
    return successResponse(c, { ...artistRef(artist), bio: artist.bio, albums: albums.map((a) => ({ ...albumRef(a), year: a.year })), top_tracks: topTracks })
  } catch (e) {
    return handleError(c, 'catalog:artist', e)
  }
})

catalogRoutes.get('/search', zValidator('query', searchSchema, validationHook), async (c) => {
  const { q } = c.req.valid('query')
  const byScore = { projection: { score: { $meta: 'textScore' } }, sort: { score: { $meta: 'textScore' } }, limit: SEARCH_LIMIT }
  try {
    const [{ data: trackHits }, { data: albums }, { data: artists }] = await Promise.all([
      MongoClientFind<TrackDoc>('catalog_tracks', { $text: { $search: q } }, { ...byScore, projection: { _id: 1, score: { $meta: 'textScore' } } }),
      MongoClientFind<AlbumDoc>('catalog_albums', { $text: { $search: q } }, byScore),
      MongoClientFind<ArtistDoc>('catalog_artists', { $text: { $search: q } }, byScore),
    ])
    return successResponse(c, {
      tracks: await loadTrackViews(trackHits.map((t) => t._id)),
      albums: albums.map(albumRef),
      artists: artists.map(artistRef),
    })
  } catch (e) {
    return handleError(c, 'catalog:search', e)
  }
})
