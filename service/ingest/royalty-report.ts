// Usage: npm run royalties -- --month 2026-11 --net-revenue-paise 1234567
// Net revenue = the Play Console earnings for the month after Google's fee and tax.
// Writes reports/royalties-<month>.csv, one row per rights holder.
import { mkdir, writeFile } from 'node:fs/promises'
import { parseArgs } from 'node:util'
import { getDatabase } from '#helpers/mongoclient.js'
import { closeClient } from '#helpers/mongodb.js'
import { splitRoyalties } from '#helpers/royalty.js'
import type { RightsHolderDoc } from '#types/docs.js'

const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000

async function main() {
  const { values } = parseArgs({ options: { month: { type: 'string' }, 'net-revenue-paise': { type: 'string' } } })
  if (!values.month || !/^\d{4}-\d{2}$/.test(values.month)) throw new Error('--month YYYY-MM is required')
  if (!values['net-revenue-paise'] || !/^\d+$/.test(values['net-revenue-paise'])) throw new Error('--net-revenue-paise is required')
  const netRevenue = BigInt(values['net-revenue-paise'])
  const [year, month] = values.month.split('-').map(Number) as [number, number]
  const from = new Date(Date.UTC(year, month - 1, 1) - IST_OFFSET_MS)
  const to = new Date(Date.UTC(year, month, 1) - IST_OFFSET_MS)

  const db = await getDatabase()
  const listening = await db.collection('play_events').aggregate<{ _id: string; premium_ms: number }>([
    { $match: { tier: 'premium', started_at: { $gte: from, $lt: to } } },
    { $lookup: { from: 'catalog_tracks', localField: 'track_id', foreignField: '_id', as: 'track' } },
    { $group: { _id: { $ifNull: [{ $first: '$track.rights_holder_id' }, 'unknown'] }, premium_ms: { $sum: '$ms_played' } } },
  ]).toArray()
  const holders = await db.collection<RightsHolderDoc>('rights_holders').find({ _id: { $in: listening.map((l) => l._id) } }).toArray()
  const holderById = new Map(holders.map((h) => [h._id, h]))

  const totalMs = listening.reduce((sum, l) => sum + l.premium_ms, 0)
  const payouts = splitRoyalties(netRevenue, listening.map((l) => ({ id: l._id, payoutShare: holderById.get(l._id)?.payout_share ?? 0, premiumMs: l.premium_ms })))
  const rows = payouts.map((p) => {
    const holder = holderById.get(p.id)
    const ms = listening.find((l) => l._id === p.id)?.premium_ms ?? 0
    return [p.id, holder?.name ?? 'MISSING AGREEMENT', holder?.email ?? '', ms, totalMs === 0 ? '0' : ((ms / totalMs) * 100).toFixed(4), p.payoutPaise.toString()]
  })

  await mkdir('reports', { recursive: true })
  const file = `reports/royalties-${values.month}.csv`
  const csv = [['rights_holder_id', 'name', 'email', 'premium_ms', 'share_percent', 'payout_paise'], ...rows].map((r) => r.map((v) => `"${String(v).replaceAll('"', '""')}"`).join(',')).join('\n')
  await writeFile(file, `${csv}\n`)
  console.log(`${file}: ${rows.length} rights holders, ${payouts.reduce((s, p) => s + p.payoutPaise, 0n)} paise to pay`)
}

main()
  .catch((e: unknown) => {
    console.error(e instanceof Error ? e.message : e)
    process.exitCode = 1
  })
  .finally(() => closeClient())
