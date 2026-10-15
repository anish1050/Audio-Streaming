import type { Document } from 'mongodb'

const TOP_GENRES = 3
// ponytail: stats bucket hours in IST; send the device time zone as a query
// parameter when the app launches outside India.
const STATS_TIME_ZONE = 'Asia/Kolkata'

export interface StatsFacets {
  totals: Array<{ total_ms: number; songs_played: number }>
  hours: Array<{ _id: number; ms: number }>
  artists: Array<{ id: string; ms: number; name?: string }>
  genres: Array<{ _id: string; ms: number }>
}

export function statsPipeline(userId: string, since: Date): Document[] {
  return [
    { $match: { user_id: userId, started_at: { $gte: since } } },
    {
      $facet: {
        totals: [
          { $group: { _id: null, total_ms: { $sum: '$ms_played' }, tracks: { $addToSet: '$track_id' } } },
          { $project: { _id: 0, total_ms: 1, songs_played: { $size: '$tracks' } } },
        ],
        hours: [
          { $group: { _id: { $hour: { date: '$started_at', timezone: STATS_TIME_ZONE } }, ms: { $sum: '$ms_played' } } },
          { $sort: { ms: -1, _id: 1 } },
          { $limit: 1 },
        ],
        artists: [
          { $group: { _id: '$artist_id', ms: { $sum: '$ms_played' } } },
          { $sort: { ms: -1, _id: 1 } },
          { $limit: 10 },
          { $lookup: { from: 'catalog_artists', localField: '_id', foreignField: '_id', as: 'artist' } },
          { $project: { _id: 0, id: '$_id', ms: 1, name: { $first: '$artist.name' } } },
        ],
        genres: [
          { $group: { _id: { $ifNull: [{ $first: '$genres' }, 'Others'] }, ms: { $sum: '$ms_played' } } },
          { $sort: { ms: -1, _id: 1 } },
        ],
      },
    },
  ]
}

/** Top genres by listening time, with the rest folded into "Others". */
export function foldGenres(genres: Array<{ _id: string; ms: number }>): Array<{ genre: string; ms: number }> {
  const top = genres.filter((g) => g._id !== 'Others').slice(0, TOP_GENRES)
  const topNames = new Set(top.map((g) => g._id))
  const othersMs = genres.filter((g) => !topNames.has(g._id)).reduce((sum, g) => sum + g.ms, 0)
  const folded = top.map((g) => ({ genre: g._id, ms: g.ms }))
  return othersMs > 0 ? [...folded, { genre: 'Others', ms: othersMs }] : folded
}
