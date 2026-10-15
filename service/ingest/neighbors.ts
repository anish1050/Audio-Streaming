// Usage: npm run ingest:neighbors
// Recomputes "sounds like" neighbours from audio embeddings for every track.
import { getDatabase } from '#helpers/mongoclient.js'
import { closeClient } from '#helpers/mongodb.js'
import { cosine } from '#helpers/vector.js'
import type { TrackDoc, TrackNeighborsDoc } from '#types/docs.js'

const NEIGHBOURS_PER_TRACK = 50

// ponytail: O(n^2) brute force, fine to ~10k tracks; switch to $vectorSearch per track beyond.
async function main() {
  const db = await getDatabase()
  const tracks = await db.collection<TrackDoc>('catalog_tracks').find({ 'embedding.0': { $exists: true } }, { projection: { embedding: 1 } }).toArray()
  const updatedAt = new Date()
  const operations = tracks.map((track) => {
    const content = tracks
      .filter((other) => other._id !== track._id)
      .map((other) => ({ track_id: other._id, score: cosine(track.embedding, other.embedding) }))
      .sort((a, b) => b.score - a.score)
      .slice(0, NEIGHBOURS_PER_TRACK)
    return { updateOne: { filter: { _id: track._id }, update: { $set: { content, updated_at: updatedAt }, $setOnInsert: { coplay: [] } }, upsert: true } }
  })
  if (operations.length > 0) await db.collection<TrackNeighborsDoc>('track_neighbors').bulkWrite(operations, { ordered: false })
  console.log(`content neighbours for ${operations.length} tracks`)
}

main()
  .catch((e: unknown) => {
    console.error(e instanceof Error ? e.message : e)
    process.exitCode = 1
  })
  .finally(() => closeClient())
