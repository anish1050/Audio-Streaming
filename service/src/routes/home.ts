import { Hono } from 'hono'
import { loadTrackViews, loadTrackViewsWhere } from '#helpers/catalog-view.js'
import { getDatabase, MongoClientFind } from '#helpers/mongoclient.js'
import { draftDailyMixes, matchPercent, type Candidate } from '#helpers/recs.js'
import { successResponse } from '#helpers/response.js'
import { handleError } from '#helpers/route.js'
import { featuredTrackIds, rankTracksForUser } from '#helpers/taste-ranking.js'
import type { AuthVariables } from '#middleware/proxy.js'
import type { PlayEventDoc } from '#types/docs.js'

const DISCOVER_SIZE = 24
const SPARKLINE_POINTS = 16
const RECENT_SIZE = 20
const FEATURED_FALLBACK_SIZE = 10

// Mixes change once a day in India time.
const indiaDate = (now: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(now)

export const homeRoutes = new Hono<{ Variables: AuthVariables }>()

homeRoutes.get('/home', async (c) => {
  const userId = c.get('userId')
  const now = new Date()
  try {
    const [ranked, featuredIds, recentIds, genres] = await Promise.all([rankTracksForUser(userId, now), featuredTrackIds(), recentTrackIds(userId), catalogGenres()])
    const mixDrafts = draftDailyMixes(ranked, `${userId}:${indiaDate(now)}`)
    const [featured, recent, mixTracks] = await Promise.all([
      featuredIds.length > 0 ? loadTrackViews(featuredIds) : loadTrackViewsWhere({}, { created_at: -1 }, FEATURED_FALLBACK_SIZE),
      loadTrackViews(recentIds),
      Promise.all(mixDrafts.map((m) => loadTrackViews(m.trackIds))),
    ])
    return successResponse(c, {
      featured,
      recent,
      daily_mixes: mixDrafts.map((m, i) => ({ id: `mix-${i + 1}`, title: `Daily Mix ${i + 1}`, subtitle: m.genre, tracks: mixTracks[i] ?? [] })),
      discover_preview: discoverPreview(ranked.slice(0, DISCOVER_SIZE)),
      genres,
    })
  } catch (e) {
    return handleError(c, 'home:get', e)
  }
})

homeRoutes.get('/discover', async (c) => {
  try {
    const top = (await rankTracksForUser(c.get('userId'), new Date())).slice(0, DISCOVER_SIZE)
    const percentById = new Map(top.map((t) => [t.trackId, matchPercent(t.cosine)]))
    const tracks = await loadTrackViews(top.map((t) => t.trackId))
    return successResponse(c, { tracks: tracks.map((t) => ({ ...t, match_percent: percentById.get(t.id) ?? 50 })) })
  } catch (e) {
    return handleError(c, 'discover:get', e)
  }
})

function discoverPreview(top: Candidate[]) {
  const percents = top.map((t) => matchPercent(t.cosine))
  const average = percents.length > 0 ? Math.round(percents.reduce((sum, p) => sum + p, 0) / percents.length) : 0
  return { count: top.length, match_percent: average, sparkline: percents.slice(0, SPARKLINE_POINTS) }
}

async function recentTrackIds(userId: string): Promise<string[]> {
  const { data: plays } = await MongoClientFind<PlayEventDoc>('play_events', { user_id: userId }, { sort: { started_at: -1 }, limit: 100, projection: { track_id: 1 } })
  return [...new Set(plays.map((p) => p.track_id))].slice(0, RECENT_SIZE)
}

async function catalogGenres(): Promise<string[]> {
  const db = await getDatabase()
  return ((await db.collection('catalog_tracks').distinct('genres')) as string[]).sort()
}
