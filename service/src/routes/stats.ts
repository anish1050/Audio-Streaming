import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { imageUrl } from '#helpers/catalog-view.js'
import { MongoClientAggregate } from '#helpers/mongoclient.js'
import { successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import { foldGenres, statsPipeline, type StatsFacets } from '#helpers/stats.js'
import type { AuthVariables } from '#middleware/proxy.js'

const PERIOD_DAYS = { '7d': 7, '30d': 30 } as const
const querySchema = z.object({ period: z.enum(['7d', '30d']).default('7d') }).strict()

export const statsRoutes = new Hono<{ Variables: AuthVariables }>()

statsRoutes.get('/', zValidator('query', querySchema, validationHook), async (c) => {
  const { period } = c.req.valid('query')
  const since = new Date(Date.now() - PERIOD_DAYS[period] * 24 * 60 * 60 * 1000)
  try {
    const { data } = await MongoClientAggregate<StatsFacets>('play_events', statsPipeline(c.get('userId'), since))
    const facets = data[0] ?? { totals: [], hours: [], artists: [], genres: [] }
    const totals = facets.totals[0] ?? { total_ms: 0, songs_played: 0 }
    return successResponse(c, {
      period,
      total_ms: totals.total_ms,
      songs_played: totals.songs_played,
      peak_hour: facets.hours[0]?._id ?? null,
      top_artists: facets.artists.map((a) => ({ id: a.id, name: a.name ?? '', image_url: imageUrl('artists', a.id), ms: a.ms })),
      genres: foldGenres(facets.genres),
    })
  } catch (e) {
    return handleError(c, 'stats:get', e)
  }
})
