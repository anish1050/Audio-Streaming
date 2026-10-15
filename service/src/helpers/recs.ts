// Pure ranking rules. The database work lives in taste-ranking.ts.

export interface Candidate {
  trackId: string
  genre: string
  cosine: number
  coplay: number
  isFresh: boolean
  wasHeardRecently: boolean
}

export const matchPercent = (cosine: number) => Math.min(99, Math.max(50, Math.round(50 + 50 * cosine)))

export const scoreCandidate = (c: Candidate) => 0.6 * c.cosine + 0.3 * c.coplay + 0.1 * (c.isFresh ? 1 : 0) - (c.wasHeardRecently ? 0.5 : 0)

/** Deterministic shuffle: the same seed (user + day) gives the same order all day. */
export function seededShuffle<T>(items: T[], seed: string): T[] {
  let state = 2166136261
  for (const ch of seed) state = Math.imul(state ^ ch.charCodeAt(0), 16777619)
  const out = [...items]
  for (let i = out.length - 1; i > 0; i--) {
    state = Math.imul(state ^ (state >>> 15), 2246822507) >>> 0
    const j = state % (i + 1)
    ;[out[i], out[j]] = [out[j]!, out[i]!]
  }
  return out
}

export interface MixDraft {
  genre: string
  trackIds: string[]
}

/** Daily Mixes: the user's top genres among ranked candidates, best tracks first, shuffled per day. */
export function draftDailyMixes(ranked: Candidate[], seed: string, mixCount = 3, mixSize = 30): MixDraft[] {
  const trackIdsByGenre = new Map<string, string[]>()
  for (const c of ranked) trackIdsByGenre.set(c.genre, [...(trackIdsByGenre.get(c.genre) ?? []), c.trackId])
  return [...trackIdsByGenre.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .slice(0, mixCount)
    .map(([genre, trackIds]) => ({ genre, trackIds: seededShuffle(trackIds.slice(0, mixSize), `${seed}:${genre}`) }))
}
