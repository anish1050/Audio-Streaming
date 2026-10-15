import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { entitlementFor } from '#helpers/entitlement.js'
import { MongoClientFindOne } from '#helpers/mongoclient.js'
import { allowedQuality } from '#helpers/quality-gate.js'
import { presignGet } from '#helpers/r2.js'
import { errorResponse, successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { AuthVariables } from '#middleware/proxy.js'
import { QUALITIES, type TrackDoc } from '#types/docs.js'

const STREAM_TTL_SECONDS = 15 * 60
const DOWNLOAD_TTL_SECONDS = 6 * 60 * 60
const querySchema = z.object({ quality: z.enum(QUALITIES) }).strict()

export const audioRoutes = new Hono<{ Variables: AuthVariables }>()

for (const purpose of ['stream', 'download'] as const) {
  audioRoutes.get(`/tracks/:id/${purpose}`, zValidator('query', querySchema, validationHook), async (c) => {
    const { quality: requested } = c.req.valid('query')
    try {
      const [{ data: track }, entitlement] = await Promise.all([
        MongoClientFindOne<TrackDoc>('catalog_tracks', { _id: c.req.param('id') }, { projection: { embedding: 0 } }),
        entitlementFor(c.get('userId'), new Date()),
      ])
      if (!track) return errorResponse(c, 'Track not found', 404)
      if (purpose === 'download' && entitlement.tier !== 'premium') return errorResponse(c, 'Premium required', 403)

      const quality = allowedQuality(requested, entitlement.tier, track)
      const asset = quality ? track.assets[quality] : undefined
      if (!quality || !asset) return errorResponse(c, 'No playable audio for this track', 404)

      const ttl = purpose === 'stream' ? STREAM_TTL_SECONDS : DOWNLOAD_TTL_SECONDS
      return successResponse(c, {
        url: await presignGet(asset.key, ttl),
        quality,
        codec: asset.codec,
        sample_rate: asset.sample_rate,
        bit_depth: asset.bit_depth,
        bytes: asset.bytes,
        loudness_lufs: track.loudness_lufs,
        expires_at: new Date(Date.now() + ttl * 1000),
      })
    } catch (e) {
      return handleError(c, `audio:${purpose}`, e)
    }
  })
}
