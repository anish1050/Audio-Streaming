import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { entitlementFor } from '#helpers/entitlement.js'
import { requireEnv } from '#helpers/env.js'
import { MongoClientFindOne, MongoClientInsertOne, MongoClientUpdateOne, isDuplicateKeyError } from '#helpers/mongoclient.js'
import { fetchPlaySubscription } from '#helpers/play-developer-api.js'
import { errorResponse, successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import { saveSubscriptionFromPlay } from '#helpers/subscription-sync.js'
import type { AuthVariables } from '#middleware/proxy.js'
import type { BillingEventDoc } from '#types/docs.js'

const verifySchema = z.object({ purchase_token: z.string().min(10).max(512) }).strict()

// Google's payloads gain fields over time, so they are parsed loosely.
const pubSubEnvelopeSchema = z.object({ message: z.object({ data: z.string(), messageId: z.string().min(1) }).passthrough() }).passthrough()
const playNotificationSchema = z.object({
  subscriptionNotification: z.object({ notificationType: z.number(), purchaseToken: z.string() }).passthrough().optional(),
}).passthrough()

export const billingRoutes = new Hono<{ Variables: AuthVariables }>()

billingRoutes.post('/google/verify', zValidator('json', verifySchema, validationHook), async (c) => {
  const { purchase_token: purchaseToken } = c.req.valid('json')
  const userId = c.get('userId')
  try {
    const state = await fetchPlaySubscription(purchaseToken)
    // The app sets this to the user id at purchase time; a token bought by
    // someone else cannot be replayed onto this account.
    if (state.externalAccountIdentifiers?.obfuscatedExternalAccountId !== userId) return errorResponse(c, 'Purchase belongs to another account', 403)
    await saveSubscriptionFromPlay(userId, purchaseToken, state)
    return successResponse(c, await entitlementFor(userId, new Date()))
  } catch (e) {
    return handleError(c, 'billing:verify', e)
  }
})

// Real-time developer notifications via Pub/Sub push. The notification is only
// a trigger: the current state is always re-read from Play. Every message is
// saved before processing; a 500 makes Pub/Sub retry.
billingRoutes.post('/google/rtdn', async (c) => {
  if (c.req.query('token') !== requireEnv('RTDN_SECRET')) return errorResponse(c, 'Unauthorized', 401)
  const envelope = pubSubEnvelopeSchema.safeParse(await c.req.json().catch(() => null))
  if (!envelope.success) return errorResponse(c, 'Bad Pub/Sub envelope', 400)
  const { messageId, data } = envelope.data.message
  const notification = decodeNotification(data)
  if (!notification) return errorResponse(c, 'Bad notification payload', 400)
  const purchaseToken = notification.subscriptionNotification?.purchaseToken ?? ''

  try {
    if (!(await recordNewEvent(messageId, purchaseToken, notification.subscriptionNotification?.notificationType ?? 0))) return successResponse(c, { duplicate: true })
    if (!purchaseToken) {
      await markProcessed(messageId)
      return successResponse(c, { test: true })
    }
    const state = await fetchPlaySubscription(purchaseToken)
    const userId = state.externalAccountIdentifiers?.obfuscatedExternalAccountId
    if (userId) await saveSubscriptionFromPlay(userId, purchaseToken, state)
    await markProcessed(messageId)
    return successResponse(c, { processed: true })
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e)
    await MongoClientUpdateOne<BillingEventDoc>('billing_events', { _id: messageId }, { $set: { error: message } }).catch(() => undefined)
    return handleError(c, 'billing:rtdn', e)
  }
})

function decodeNotification(base64: string): z.infer<typeof playNotificationSchema> | null {
  try {
    const parsed = playNotificationSchema.safeParse(JSON.parse(Buffer.from(base64, 'base64').toString('utf8')))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}

/** False when this message was already processed; a retry of a failed one returns true. */
async function recordNewEvent(messageId: string, purchaseToken: string, notificationType: number): Promise<boolean> {
  try {
    await MongoClientInsertOne<BillingEventDoc>('billing_events', { _id: messageId, purchase_token: purchaseToken, notification_type: notificationType, received_at: new Date() })
    return true
  } catch (e) {
    if (!isDuplicateKeyError(e)) throw e
    const { data: existing } = await MongoClientFindOne<BillingEventDoc>('billing_events', { _id: messageId })
    return !existing?.processed_at
  }
}

async function markProcessed(messageId: string): Promise<void> {
  await MongoClientUpdateOne<BillingEventDoc>('billing_events', { _id: messageId }, { $set: { processed_at: new Date() }, $unset: { error: '' } })
}
