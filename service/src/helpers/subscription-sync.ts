import { MongoClientUpdateOne } from './mongoclient.js'
import { acknowledgePlaySubscription, type PlaySubscription } from './play-developer-api.js'
import type { SubscriptionDoc } from '#types/docs.js'

/** The only writer of `subscriptions`: stores Play's current state for a user and acknowledges a new purchase. */
export async function saveSubscriptionFromPlay(userId: string, purchaseToken: string, state: PlaySubscription): Promise<void> {
  const latest = [...(state.lineItems ?? [])].sort((a, b) => Date.parse(b.expiryTime) - Date.parse(a.expiryTime))[0]
  if (!latest) throw new Error('Play subscription has no line items')

  await MongoClientUpdateOne<SubscriptionDoc>(
    'subscriptions',
    { _id: userId },
    {
      $set: {
        provider: 'google_play',
        purchase_token: purchaseToken,
        product_id: latest.productId,
        base_plan_id: latest.offerDetails?.basePlanId ?? '',
        state: state.subscriptionState,
        expires_at: new Date(latest.expiryTime),
        auto_renewing: latest.autoRenewingPlan?.autoRenewEnabled ?? false,
        verified_at: new Date(),
      },
    },
    { upsert: true },
  )
  if (state.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING') await acknowledgePlaySubscription(latest.productId, purchaseToken)
}
