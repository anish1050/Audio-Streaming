import { Hono } from 'hono'
import { MongoClientFind } from '#helpers/mongoclient.js'
import { successResponse } from '#helpers/response.js'
import { handleError } from '#helpers/route.js'
import type { ArtistDoc, TrackDoc } from '#types/docs.js'

// Creative Commons BY tracks must credit the artist and licence. Public.
export const creditRoutes = new Hono()

creditRoutes.get('/', async (c) => {
  try {
    const { data: tracks } = await MongoClientFind<TrackDoc>('catalog_tracks', { licence: { $regex: '^CC-BY' } }, { projection: { title: 1, artist_id: 1, licence: 1, attribution: 1 }, sort: { title: 1 } })
    const { data: artists } = await MongoClientFind<ArtistDoc>('catalog_artists', { _id: { $in: [...new Set(tracks.map((t) => t.artist_id))] } }, { projection: { name: 1 } })
    const artistName = new Map(artists.map((a) => [a._id, a.name]))
    return successResponse(c, { credits: tracks.map((t) => ({ track_id: t._id, title: t.title, artist: artistName.get(t.artist_id) ?? '', licence: t.licence, attribution: t.attribution })) })
  } catch (e) {
    return handleError(c, 'credits', e)
  }
})
