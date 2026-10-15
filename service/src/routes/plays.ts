import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { entitlementFor } from '#helpers/entitlement.js'
import { MongoClientFind, MongoClientInsertManyIgnoringDuplicates } from '#helpers/mongoclient.js'
import { errorResponse, successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { AuthVariables } from '#middleware/proxy.js'
import { QUALITIES, type PlayEventDoc, type TrackDoc } from '#types/docs.js'

const MIN_MS_PLAYED = 3000
const DURATION_SLACK_MS = 5000
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000
const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000

const bodySchema = z.object({
  events: z.array(z.object({
    client_event_id: z.string().uuid(),
    track_id: z.string().min(1).max(64),
    ms_played: z.number().int().min(MIN_MS_PLAYED),
    started_at: z.coerce.date(),
    quality: z.enum(QUALITIES),
    offline: z.boolean(),
  }).strict()).min(1).max(200),
}).strict()

export const playRoutes = new Hono<{ Variables: AuthVariables }>()

// Idempotent on client_event_id, so the app can resend a batch after a timeout.
playRoutes.post('/', zValidator('json', bodySchema, validationHook), async (c) => {
  const { events } = c.req.valid('json')
  const userId = c.get('userId')
  const now = Date.now()
  if (events.some((e) => e.started_at.getTime() > now + MAX_CLOCK_SKEW_MS || e.started_at.getTime() < now - MAX_AGE_MS)) {
    return errorResponse(c, 'started_at must be within the last 30 days', 400)
  }
  try {
    const trackIds = [...new Set(events.map((e) => e.track_id))]
    const [{ data: tracks }, entitlement] = await Promise.all([
      MongoClientFind<TrackDoc>('catalog_tracks', { _id: { $in: trackIds } }, { projection: { artist_id: 1, genres: 1, duration_ms: 1 } }),
      entitlementFor(userId, new Date(now)),
    ])
    const trackById = new Map(tracks.map((t) => [t._id, t]))
    if (events.some((e) => e.ms_played > (trackById.get(e.track_id)?.duration_ms ?? Infinity) + DURATION_SLACK_MS)) {
      return errorResponse(c, 'ms_played exceeds the track duration', 400)
    }

    // Events for tracks that were taken down since are dropped, not rejected.
    const docs: PlayEventDoc[] = events.flatMap((e) => {
      const track = trackById.get(e.track_id)
      if (!track) return []
      return [{
        _id: e.client_event_id, user_id: userId, track_id: e.track_id, artist_id: track.artist_id, genres: track.genres, ms_played: e.ms_played,
        duration_ms: track.duration_ms, quality: e.quality, offline: e.offline, tier: entitlement.tier, started_at: e.started_at,
      }]
    })
    const stored = await MongoClientInsertManyIgnoringDuplicates<PlayEventDoc>('play_events', docs)
    return successResponse(c, { accepted: events.length, stored })
  } catch (e) {
    return handleError(c, 'plays:post', e)
  }
})
