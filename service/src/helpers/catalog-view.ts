import { MongoClientFind } from './mongoclient.js'
import { bestAsset } from './quality-gate.js'
import type { AlbumDoc, ArtistDoc, Quality, TrackDoc } from '#types/docs.js'

// Public shapes. Storage keys and embeddings never leave the server.

// Images go through a stable redirect URL so the app can cache them, even
// though the presigned R2 URL behind it changes every time.
export const imageUrl = (kind: 'albums' | 'artists', id: string) => `/api/images/${kind}/${id}`

export interface ArtistRef {
  id: string
  name: string
  image_url: string
}

export interface AlbumRef {
  id: string
  title: string
  cover_url: string
}

export interface TrackView {
  id: string
  title: string
  track_no: number
  duration_ms: number
  explicit: boolean
  genres: string[]
  loudness_lufs: number
  album: AlbumRef
  artist: ArtistRef
  best_quality: { quality: Quality; codec: string; sample_rate: number; bit_depth: number } | null
}

const TRACK_PROJECTION = { embedding: 0 }

export const artistRef = (artist: Pick<ArtistDoc, '_id' | 'name'>): ArtistRef => ({ id: artist._id, name: artist.name, image_url: imageUrl('artists', artist._id) })
export const albumRef = (album: Pick<AlbumDoc, '_id' | 'title'>): AlbumRef => ({ id: album._id, title: album.title, cover_url: imageUrl('albums', album._id) })

export function toTrackView(track: TrackDoc, album: AlbumDoc | undefined, artist: ArtistDoc | undefined): TrackView {
  const best = bestAsset(track.assets)
  return {
    id: track._id,
    title: track.title,
    track_no: track.track_no,
    duration_ms: track.duration_ms,
    explicit: track.explicit,
    genres: track.genres,
    loudness_lufs: track.loudness_lufs,
    album: albumRef(album ?? { _id: track.album_id, title: '' }),
    artist: artistRef(artist ?? { _id: track.artist_id, name: '' }),
    best_quality: best && { quality: best.quality, codec: best.codec, sample_rate: best.sample_rate, bit_depth: best.bit_depth },
  }
}

/** Track views in the order of `trackIds`; unknown ids are dropped. */
export async function loadTrackViews(trackIds: string[]): Promise<TrackView[]> {
  if (trackIds.length === 0) return []
  const { data: tracks } = await MongoClientFind<TrackDoc>('catalog_tracks', { _id: { $in: trackIds } }, { projection: TRACK_PROJECTION })
  return viewsFor(tracks, trackIds)
}

export async function loadTrackViewsWhere(filter: Record<string, unknown>, sort: Record<string, 1 | -1>, limit: number): Promise<TrackView[]> {
  const { data: tracks } = await MongoClientFind<TrackDoc>('catalog_tracks', filter, { projection: TRACK_PROJECTION, sort, limit })
  return viewsFor(tracks, tracks.map((t) => t._id))
}

async function viewsFor(tracks: TrackDoc[], order: string[]): Promise<TrackView[]> {
  const albumIds = [...new Set(tracks.map((t) => t.album_id))]
  const artistIds = [...new Set(tracks.map((t) => t.artist_id))]
  const [{ data: albums }, { data: artists }] = await Promise.all([
    MongoClientFind<AlbumDoc>('catalog_albums', { _id: { $in: albumIds } }),
    MongoClientFind<ArtistDoc>('catalog_artists', { _id: { $in: artistIds } }),
  ])
  const albumById = new Map(albums.map((a) => [a._id, a]))
  const artistById = new Map(artists.map((a) => [a._id, a]))
  const trackById = new Map(tracks.map((t) => [t._id, t]))
  return order.flatMap((id) => {
    const track = trackById.get(id)
    return track ? [toTrackView(track, albumById.get(track.album_id), artistById.get(track.artist_id))] : []
  })
}
