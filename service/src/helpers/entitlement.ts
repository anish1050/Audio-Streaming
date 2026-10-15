import { MongoClientFindOne } from './mongoclient.js'
import type { SubscriptionDoc, Tier } from '#types/docs.js'

// Cancelled-but-paid-up and grace-period users keep Premium until expires_at.
const PAID_UP_STATES = new Set(['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED'])

export function tierFor(subscription: Pick<SubscriptionDoc, 'state' | 'expires_at'> | null, now: Date): Tier {
  return subscription !== null && PAID_UP_STATES.has(subscription.state) && subscription.expires_at > now ? 'premium' : 'free'
}

export interface Entitlement {
  tier: Tier
  expires_at: Date | null
  verified_at: Date | null
}

export async function entitlementFor(userId: string, now: Date): Promise<Entitlement> {
  const { data: subscription } = await MongoClientFindOne<SubscriptionDoc>('subscriptions', { _id: userId })
  return {
    tier: tierFor(subscription, now),
    expires_at: subscription?.expires_at ?? null,
    verified_at: subscription?.verified_at ?? null,
  }
}
