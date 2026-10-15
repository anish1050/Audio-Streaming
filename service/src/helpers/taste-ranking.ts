import { ObjectId } from 'mongodb'
import { MongoClientFind, MongoClientFindOne } from './mongoclient.js'
import { scoreCandidate, type Candidate } from './recs.js'
import { cosine, meanVector } from './vector.js'
import { findNearestTrackIds } from './vector-search.js'
import type { CoreUserDoc, LibraryItemDoc, PlayEventDoc, PlaylistDoc, TrackDoc, TrackNeighborsDoc } from '#types/docs.js'

const DAY_MS = 24 * 60 * 60 * 1000
const TASTE_PLAYS = 200
const COPLAY_SEEDS = 20
const NEAREST = 100
const LIKED_WEIGHT = 2

/**
 * Tracks ranked for this user, best first. Taste is the weighted mean of the
 * audio embeddings of what they played and liked (onboarding artists when
 * they have neither); candidates come from vector search, co-play neighbours
 * and editorial picks.
 */
export async function rankTracksForUser(userId: string, now: Date): Promise<Candidate[]> {
  const [{ data: plays }, { data: liked }] = await Promise.all([
    MongoClientFind<PlayEventDoc>('play_events', { user_id: userId }, { sort: { started_at: -1 }, limit: TASTE_PLAYS, projection: { track_id: 1, ms_played: 1, duration_ms: 1, started_at: 1 } }),
    MongoClientFind<LibraryItemDoc>('library_items', { user_id: userId, kind: 'track' }, { projection: { item_id: 1 } }),
  ])

  const weightByTrack = new Map<string, number>()
  for (const p of plays) weightByTrack.set(p.track_id, (weightByTrack.get(p.track_id) ?? 0) + Math.min(1, p.ms_played / Math.max(1, p.duration_ms)))
  for (const l of liked) weightByTrack.set(l.item_id, (weightByTrack.get(l.item_id) ?? 0) + LIKED_WEIGHT)
  const taste = (await tasteFromTracks(weightByTrack)) ?? (await tasteFromOnboarding(userId))

  const recentTrackIds = [...new Set(plays.map((p) => p.track_id))].slice(0, COPLAY_SEEDS)
  const [nearestIds, coplayById, editorialIds] = await Promise.all([
    taste ? findNearestTrackIds(taste, NEAREST) : Promise.resolve([]),
    coplayScores(recentTrackIds),
    featuredTrackIds(),
  ])

  const candidateIds = [...new Set([...nearestIds, ...coplayById.keys(), ...editorialIds])]
  if (candidateIds.length === 0) return []
  const { data: tracks } = await MongoClientFind<TrackDoc>('catalog_tracks', { _id: { $in: candidateIds } }, { projection: { embedding: 1, genres: 1, created_at: 1 } })

  const heardRecently = new Set(plays.filter((p) => p.started_at.getTime() > now.getTime() - 14 * DAY_MS).map((p) => p.track_id))
  return tracks
    .map((t): Candidate => ({
      trackId: t._id,
      genre: t.genres[0] ?? 'Others',
      cosine: taste && t.embedding.length > 0 ? cosine(taste, t.embedding) : 0,
      coplay: coplayById.get(t._id) ?? 0,
      isFresh: t.created_at.getTime() > now.getTime() - 30 * DAY_MS,
      wasHeardRecently: heardRecently.has(t._id),
    }))
    .sort((a, b) => scoreCandidate(b) - scoreCandidate(a) || a.trackId.localeCompare(b.trackId))
}

async function tasteFromTracks(weightByTrack: Map<string, number>): Promise<number[] | null> {
  if (weightByTrack.size === 0) return null
  const { data: tracks } = await MongoClientFind<TrackDoc>('catalog_tracks', { _id: { $in: [...weightByTrack.keys()] }, 'embedding.0': { $exists: true } }, { projection: { embedding: 1 } })
  return meanVector(tracks.map((t) => t.embedding), tracks.map((t) => weightByTrack.get(t._id) ?? 0))
}

async function tasteFromOnboarding(userId: string): Promise<number[] | null> {
  const { data: user } = await MongoClientFindOne<CoreUserDoc>('core_users', { _id: new ObjectId(userId) }, { projection: { taste_artist_ids: 1 } })
  const artistIds = user?.taste_artist_ids ?? []
  if (artistIds.length === 0) return null
  const { data: tracks } = await MongoClientFind<TrackDoc>('catalog_tracks', { artist_id: { $in: artistIds }, 'embedding.0': { $exists: true } }, { projection: { embedding: 1 }, limit: 200 })
  return meanVector(tracks.map((t) => t.embedding), tracks.map(() => 1))
}

async function coplayScores(seedTrackIds: string[]): Promise<Map<string, number>> {
  const scores = new Map<string, number>()
  if (seedTrackIds.length === 0) return scores
  const { data: neighbours } = await MongoClientFind<TrackNeighborsDoc>('track_neighbors', { _id: { $in: seedTrackIds } }, { projection: { coplay: 1 } })
  for (const n of neighbours) for (const c of n.coplay) scores.set(c.track_id, Math.max(scores.get(c.track_id) ?? 0, c.score))
  return scores
}

export async function featuredTrackIds(): Promise<string[]> {
  const { data: featured } = await MongoClientFindOne<PlaylistDoc>('catalog_playlists', { _id: 'featured' })
  return featured?.track_ids ?? []
}
