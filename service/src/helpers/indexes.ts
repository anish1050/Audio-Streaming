import { getDatabase } from './mongoclient.js'

let ensured: Promise<void> | null = null

// Idempotent; runs once per process. The Atlas vector index `track_embedding`
// is created in the Atlas UI because mongodb-memory-server cannot host it.
export function ensureIndexes(): Promise<void> {
  ensured ??= createIndexes().catch((e) => {
    ensured = null
    throw e
  })
  return ensured
}

async function createIndexes(): Promise<void> {
  const db = await getDatabase()
  await Promise.all([
    db.collection('core_users').createIndex({ google_sub: 1 }, { unique: true }),
    db.collection('catalog_artists').createIndex({ slug: 1 }, { unique: true }),
    db.collection('catalog_artists').createIndex({ name: 'text' }),
    db.collection('catalog_albums').createIndex({ artist_id: 1 }),
    db.collection('catalog_albums').createIndex({ title: 'text' }),
    db.collection('catalog_tracks').createIndex({ album_id: 1, track_no: 1 }),
    db.collection('catalog_tracks').createIndex({ artist_id: 1 }),
    db.collection('catalog_tracks').createIndex({ fp_id: 1 }, { unique: true }),
    db.collection('catalog_tracks').createIndex({ title: 'text' }),
    db.collection('library_items').createIndex({ user_id: 1, kind: 1, item_id: 1 }, { unique: true }),
    db.collection('play_events').createIndex({ user_id: 1, started_at: -1 }),
    db.collection('play_events').createIndex({ track_id: 1, started_at: -1 }),
    db.collection('play_events').createIndex({ started_at: -1 }),
    db.collection('subscriptions').createIndex({ purchase_token: 1 }, { unique: true }),
    db.collection('fingerprints').createIndex({ h: 1 }),
    db.collection('fingerprints').createIndex({ tid: 1 }),
  ])
}
