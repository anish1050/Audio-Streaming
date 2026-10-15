// Usage: npm run ingest:remove -- --track <track_id>
// Takedown: removes audio, catalog entry, fingerprints and every neighbour link.
import { parseArgs } from 'node:util'
import { DeleteObjectsCommand, ListObjectsV2Command } from '@aws-sdk/client-s3'
import { requireEnv } from '#helpers/env.js'
import { getDatabase } from '#helpers/mongoclient.js'
import { closeClient } from '#helpers/mongodb.js'
import { r2Client } from '#helpers/r2.js'
import type { FingerprintDoc, PlaylistDoc, TrackDoc, TrackNeighborsDoc } from '#types/docs.js'

async function main() {
  const { values } = parseArgs({ options: { track: { type: 'string' } } })
  const trackId = values.track
  if (!trackId) throw new Error('--track is required')

  const bucket = requireEnv('R2_BUCKET')
  const listed = await r2Client().send(new ListObjectsV2Command({ Bucket: bucket, Prefix: `audio/${trackId}/` }))
  const objects = (listed.Contents ?? []).flatMap((o) => (o.Key ? [{ Key: o.Key }] : []))
  if (objects.length > 0) await r2Client().send(new DeleteObjectsCommand({ Bucket: bucket, Delete: { Objects: objects } }))

  const db = await getDatabase()
  const track = await db.collection<TrackDoc>('catalog_tracks').findOneAndDelete({ _id: trackId })
  if (track) await db.collection<FingerprintDoc>('fingerprints').deleteMany({ tid: track.fp_id })
  await db.collection<TrackNeighborsDoc>('track_neighbors').deleteOne({ _id: trackId })
  await db.collection<TrackNeighborsDoc>('track_neighbors').updateMany({}, { $pull: { content: { track_id: trackId }, coplay: { track_id: trackId } } })
  await db.collection<PlaylistDoc>('catalog_playlists').updateMany({}, { $pull: { track_ids: trackId } })
  await db.collection('library_items').deleteMany({ kind: 'track', item_id: trackId })
  console.log(`removed ${trackId}: ${objects.length} audio files, catalog entry ${track ? 'deleted' : 'not found'}`)
}

main()
  .catch((e: unknown) => {
    console.error(e instanceof Error ? e.message : e)
    process.exitCode = 1
  })
  .finally(() => closeClient())
