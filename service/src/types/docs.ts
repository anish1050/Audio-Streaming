import type { ObjectId } from 'mongodb'

export const QUALITIES = ['preview', 'low', 'high', 'lossless', 'hires'] as const
export type Quality = (typeof QUALITIES)[number]
export type Tier = 'free' | 'premium'

export interface CoreUserDoc {
  _id: ObjectId
  google_sub: string
  email: string
  name: string
  picture: string
  taste_genres: string[]
  taste_artist_ids: string[]
  created_at: Date
  last_login_at: Date
}

export interface AssetInfo {
  key: string
  codec: 'flac' | 'opus'
  sample_rate: number
  bit_depth: number
  bitrate_kbps: number
  bytes: number
}

export interface TrackDoc {
  _id: string
  fp_id: number
  album_id: string
  artist_id: string
  title: string
  track_no: number
  duration_ms: number
  isrc: string
  explicit: boolean
  genres: string[]
  loudness_lufs: number
  free_full_length: boolean
  assets: Partial<Record<Quality, AssetInfo>>
  embedding: number[]
  licence: string
  attribution: string
  rights_holder_id: string
  created_at: Date
}

export interface AlbumDoc {
  _id: string
  artist_id: string
  title: string
  year: number
  cover_key: string
  upc: string
  genres: string[]
  created_at: Date
}

export interface ArtistDoc {
  _id: string
  name: string
  slug: string
  bio: string
  image_key: string
  rights_holder_id: string
  created_at: Date
}

export interface PlaylistDoc {
  _id: string
  kind: 'editorial'
  title: string
  subtitle: string
  cover_key: string
  track_ids: string[]
  updated_at: Date
}

export interface RightsHolderDoc {
  _id: string
  name: string
  email: string
  payout_share: number
  agreement_signed_at: Date
  agreement_doc_key: string
  payout_upi_masked: string
}

export const LIBRARY_KINDS = ['track', 'album', 'artist', 'playlist'] as const
export type LibraryKind = (typeof LIBRARY_KINDS)[number]

export interface LibraryItemDoc {
  user_id: string
  kind: LibraryKind
  item_id: string
  created_at: Date
}

export interface PlayEventDoc {
  _id: string
  user_id: string
  track_id: string
  artist_id: string
  genres: string[]
  ms_played: number
  duration_ms: number
  quality: Quality
  offline: boolean
  tier: Tier
  started_at: Date
}

export interface SubscriptionDoc {
  _id: string
  provider: 'google_play'
  purchase_token: string
  product_id: string
  base_plan_id: string
  state: string
  expires_at: Date
  auto_renewing: boolean
  verified_at: Date
}

export interface BillingEventDoc {
  _id: string
  purchase_token: string
  notification_type: number
  received_at: Date
  processed_at?: Date
  error?: string
}

export interface FingerprintDoc {
  h: number
  t: number
  tid: number
}

export interface NeighborScore {
  track_id: string
  score: number
}

export interface TrackNeighborsDoc {
  _id: string
  content: NeighborScore[]
  coplay: NeighborScore[]
  updated_at: Date
}

export interface CatalogRequestDoc {
  user_id: string
  title: string
  artist: string
  created_at: Date
}

export interface CounterDoc {
  _id: string
  seq: number
}
