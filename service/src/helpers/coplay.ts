import type { NeighborScore } from '#types/docs.js'

const NEIGHBOURS_PER_TRACK = 50

/**
 * Item-to-item neighbours from who listened to what:
 * score(a, b) = users who played both / sqrt(users of a * users of b).
 */
// ponytail: in-memory pair counting, O(users x tracks-per-user^2). Fine below
// ~10k active users; move to a batch job or aggregation pipeline beyond that.
export function coplayNeighbours(tracksByUser: Map<string, Set<string>>): Map<string, NeighborScore[]> {
  const usersPerTrack = new Map<string, number>()
  const pairCounts = new Map<string, Map<string, number>>()
  for (const tracks of tracksByUser.values()) {
    const list = [...tracks]
    for (const a of list) {
      usersPerTrack.set(a, (usersPerTrack.get(a) ?? 0) + 1)
      const counts = pairCounts.get(a) ?? new Map<string, number>()
      for (const b of list) if (a !== b) counts.set(b, (counts.get(b) ?? 0) + 1)
      pairCounts.set(a, counts)
    }
  }

  const neighbours = new Map<string, NeighborScore[]>()
  for (const [a, counts] of pairCounts) {
    const usersA = usersPerTrack.get(a) ?? 1
    const scored = [...counts.entries()]
      .map(([b, shared]) => ({ track_id: b, score: shared / Math.sqrt(usersA * (usersPerTrack.get(b) ?? 1)) }))
      .sort((x, y) => y.score - x.score || x.track_id.localeCompare(y.track_id))
      .slice(0, NEIGHBOURS_PER_TRACK)
    if (scored.length > 0) neighbours.set(a, scored)
  }
  return neighbours
}
