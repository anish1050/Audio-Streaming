import { getDatabase } from '#helpers/mongoclient.js'
import type { AlbumDoc, ArtistDoc, AssetInfo, Quality, TrackDoc } from '#types/docs.js'

export const asset = (quality: Quality, sampleRate = 96000, bitDepth = 24): AssetInfo => ({
  key: `audio/t/${quality}`, codec: quality === 'hires' || quality === 'lossless' ? 'flac' : 'opus', sample_rate: sampleRate, bit_depth: bitDepth, bitrate_kbps: 1000, bytes: 1000,
})

const allAssets = { hires: asset('hires'), lossless: asset('lossless', 44100, 16), high: asset('high', 48000, 16), low: asset('low', 48000, 16), preview: asset('preview', 48000, 16) }

export function trackDoc(overrides: Partial<TrackDoc> & Pick<TrackDoc, '_id' | 'title'>): TrackDoc {
  return {
    fp_id: Math.floor(Math.random() * 1e9), album_id: 'album-night', artist_id: 'artist-arjun', track_no: 1, duration_ms: 240000, isrc: '', explicit: false,
    genres: ['lo-fi'], loudness_lufs: -12, free_full_length: false, assets: allAssets, embedding: [], licence: 'direct', attribution: '',
    rights_holder_id: 'rh-arjun', created_at: new Date(), ...overrides,
  }
}

export async function seedCatalog() {
  const db = await getDatabase()
  const artists: ArtistDoc[] = [
    { _id: 'artist-arjun', name: 'Arjun Rao', slug: 'arjun-rao', bio: 'Night drives', rights_holder_id: 'rh-arjun', created_at: new Date() },
    { _id: 'artist-mira', name: 'Mira Sen', slug: 'mira-sen', bio: 'Rooftops', rights_holder_id: 'rh-mira', created_at: new Date() },
  ]
  const albums: AlbumDoc[] = [
    { _id: 'album-night', artist_id: 'artist-arjun', title: 'Neon City', year: 2026, upc: '', genres: ['lo-fi'], created_at: new Date() },
    { _id: 'album-glass', artist_id: 'artist-mira', title: 'Glass', year: 2025, upc: '', genres: ['pop'], created_at: new Date() },
  ]
  const tracks: TrackDoc[] = [
    trackDoc({ _id: 'track-midnight', title: 'Midnight Drive', track_no: 2 }),
    trackDoc({ _id: 'track-neon', title: 'Neon Rain', track_no: 1 }),
    trackDoc({ _id: 'track-rooftops', title: 'Glass Rooftops', album_id: 'album-glass', artist_id: 'artist-mira', genres: ['pop'], rights_holder_id: 'rh-mira' }),
    trackDoc({ _id: 'track-cd', title: 'Paper Lanterns', album_id: 'album-glass', artist_id: 'artist-mira', genres: ['pop'], free_full_length: true, rights_holder_id: 'rh-mira',
      assets: { lossless: asset('lossless', 44100, 16), high: asset('high', 48000, 16), low: asset('low', 48000, 16), preview: asset('preview', 48000, 16) } }),
  ]
  await db.collection<ArtistDoc>('catalog_artists').insertMany(artists)
  await db.collection<AlbumDoc>('catalog_albums').insertMany(albums)
  await db.collection<TrackDoc>('catalog_tracks').insertMany(tracks)
}
