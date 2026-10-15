// Usage: npm run ingest:featured -- --tracks id1,id2,id3 [--title "Featured"] [--subtitle "Sounds"]
// Sets the editorial playlist shown at the top of Home and used for cold starts.
import { parseArgs } from 'node:util'
import { getDatabase } from '#helpers/mongoclient.js'
import { closeClient } from '#helpers/mongodb.js'
import type { PlaylistDoc, TrackDoc } from '#types/docs.js'

async function main() {
  const { values } = parseArgs({ options: { tracks: { type: 'string' }, title: { type: 'string', default: 'Featured' }, subtitle: { type: 'string', default: 'Sounds' } } })
  const trackIds = (values.tracks ?? '').split(',').map((id) => id.trim()).filter(Boolean)
  if (trackIds.length === 0) throw new Error('--tracks is required')

  const db = await getDatabase()
  const found = await db.collection<TrackDoc>('catalog_tracks').distinct('_id', { _id: { $in: trackIds } })
  const missing = trackIds.filter((id) => !found.includes(id))
  if (missing.length > 0) throw new Error(`unknown tracks: ${missing.join(', ')}`)

  await db.collection<PlaylistDoc>('catalog_playlists').updateOne(
    { _id: 'featured' },
    { $set: { kind: 'editorial', title: values.title, subtitle: values.subtitle, track_ids: trackIds, updated_at: new Date() } },
    { upsert: true },
  )
  console.log(`featured: ${trackIds.length} tracks`)
}

main()
  .catch((e: unknown) => {
    console.error(e instanceof Error ? e.message : e)
    process.exitCode = 1
  })
  .finally(() => closeClient())
