import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { recognizeExternally } from '#helpers/audd.js'
import { loadTrackViews } from '#helpers/catalog-view.js'
import { bestMatch, landmarks, pcm16ToFloat } from '#helpers/fingerprint.js'
import { MongoClientFind, MongoClientFindOne, MongoClientInsertOne } from '#helpers/mongoclient.js'
import { errorResponse, successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import { rateLimit } from '#middleware/rate-limit.js'
import type { AuthVariables } from '#middleware/proxy.js'
import type { CatalogRequestDoc, FingerprintDoc, TrackDoc } from '#types/docs.js'

// 16 kHz mono 16-bit: 2 seconds minimum, 10 seconds maximum.
const MIN_CLIP_BYTES = 64_000
const MAX_CLIP_BYTES = 320_000

const requestSchema = z.object({ title: z.string().trim().min(1).max(120), artist: z.string().trim().max(120).default('') }).strict()

export const recognizeRoutes = new Hono<{ Variables: AuthVariables }>()

recognizeRoutes.post('/', rateLimit(10, 60, 'recognize'), async (c) => {
  const declaredLength = Number(c.req.header('content-length') ?? 0)
  if (declaredLength > MAX_CLIP_BYTES) return errorResponse(c, 'Clip too long', 413)
  const clip = new Uint8Array(await c.req.arrayBuffer())
  if (clip.byteLength > MAX_CLIP_BYTES) return errorResponse(c, 'Clip too long', 413)
  if (clip.byteLength < MIN_CLIP_BYTES) return errorResponse(c, 'Clip too short', 400)

  try {
    const query = landmarks(pcm16ToFloat(clip))
    const hashes = [...new Set(query.map((q) => q.h))]
    const { data: stored } = hashes.length > 0
      ? await MongoClientFind<FingerprintDoc>('fingerprints', { h: { $in: hashes } }, { projection: { _id: 0, h: 1, t: 1, tid: 1 } })
      : { data: [] }
    const match = bestMatch(query, stored)
    if (match) {
      const { data: track } = await MongoClientFindOne<TrackDoc>('catalog_tracks', { fp_id: match.tid }, { projection: { _id: 1 } })
      const [view] = track ? await loadTrackViews([track._id]) : []
      if (view) return successResponse(c, { match: { track: view, offset_ms: match.offsetMs, confidence: match.confidence }, external: null })
    }
    return successResponse(c, { match: null, external: await recognizeExternally(clip, new Date()) })
  } catch (e) {
    return handleError(c, 'recognize', e)
  }
})

recognizeRoutes.post('/request', zValidator('json', requestSchema, validationHook), async (c) => {
  const { title, artist } = c.req.valid('json')
  try {
    await MongoClientInsertOne<CatalogRequestDoc>('catalog_requests', { user_id: c.get('userId'), title, artist, created_at: new Date() })
    return successResponse(c, { requested: true }, 201)
  } catch (e) {
    return handleError(c, 'recognize:request', e)
  }
})
