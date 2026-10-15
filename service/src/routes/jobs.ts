import { Hono } from 'hono'
import { coplayNeighbours } from '#helpers/coplay.js'
import { requireEnv } from '#helpers/env.js'
import { MongoClientBulkWrite, MongoClientFind } from '#helpers/mongoclient.js'
import { errorResponse, successResponse } from '#helpers/response.js'
import { handleError } from '#helpers/route.js'
import type { PlayEventDoc, TrackNeighborsDoc } from '#types/docs.js'

const WINDOW_MS = 30 * 24 * 60 * 60 * 1000
const MIN_LISTEN_MS = 30_000

// Called nightly by Cloud Scheduler with Authorization: Bearer <CRON_SECRET>.
export const jobRoutes = new Hono()

jobRoutes.use('*', async (c, next) => {
  if (c.req.header('Authorization') !== `Bearer ${requireEnv('CRON_SECRET')}`) return errorResponse(c, 'Unauthorized', 401)
  await next()
})

jobRoutes.post('/coplay', async (c) => {
  try {
    const since = new Date(Date.now() - WINDOW_MS)
    const { data: plays } = await MongoClientFind<PlayEventDoc>('play_events', { started_at: { $gte: since }, ms_played: { $gte: MIN_LISTEN_MS } }, { projection: { user_id: 1, track_id: 1 } })
    const tracksByUser = new Map<string, Set<string>>()
    for (const p of plays) tracksByUser.set(p.user_id, (tracksByUser.get(p.user_id) ?? new Set()).add(p.track_id))

    const neighbours = coplayNeighbours(tracksByUser)
    const updatedAt = new Date()
    await MongoClientBulkWrite<TrackNeighborsDoc>('track_neighbors', [...neighbours].map(([trackId, coplay]) => ({
      updateOne: { filter: { _id: trackId }, update: { $set: { coplay, updated_at: updatedAt }, $setOnInsert: { content: [] } }, upsert: true },
    })))
    return successResponse(c, { tracks: neighbours.size, users: tracksByUser.size })
  } catch (e) {
    return handleError(c, 'jobs:coplay', e)
  }
})
