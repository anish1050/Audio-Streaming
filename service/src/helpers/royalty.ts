export interface HolderListening {
  id: string
  payoutShare: number
  premiumMs: number
}

/**
 * Pro-rata payout: each rights holder gets their payout share of net revenue,
 * weighted by their portion of premium listening time. Integer paise
 * arithmetic floors every share, so the total never exceeds the pool.
 */
export function splitRoyalties(netRevenuePaise: bigint, holders: HolderListening[]): Array<{ id: string; payoutPaise: bigint }> {
  const totalMs = holders.reduce((sum, h) => sum + BigInt(h.premiumMs), 0n)
  return holders.map((h) => ({
    id: h.id,
    payoutPaise: totalMs === 0n ? 0n : (netRevenuePaise * BigInt(Math.round(h.payoutShare * 10_000)) * BigInt(h.premiumMs)) / (10_000n * totalMs),
  }))
}
