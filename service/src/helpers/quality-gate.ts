import type { AssetInfo, Quality, Tier, TrackDoc } from '#types/docs.js'

// Best first. `preview` sits outside the ladder: only free users and explicit
// preview requests get it.
export const QUALITY_LADDER: Quality[] = ['hires', 'lossless', 'high', 'low']

export function allowedQuality(requested: Quality, tier: Tier, track: Pick<TrackDoc, 'free_full_length' | 'assets'>): Quality | null {
  if (tier === 'free') {
    if (track.free_full_length && track.assets.low) return 'low'
    return track.assets.preview ? 'preview' : null
  }
  if (requested === 'preview') return track.assets.preview ? 'preview' : null
  return QUALITY_LADDER.slice(QUALITY_LADDER.indexOf(requested)).find((q) => track.assets[q]) ?? null
}

export function bestAsset(assets: TrackDoc['assets']): (AssetInfo & { quality: Quality }) | null {
  const quality = QUALITY_LADDER.find((q) => assets[q])
  const asset = quality ? assets[quality] : undefined
  return quality && asset ? { ...asset, quality } : null
}
