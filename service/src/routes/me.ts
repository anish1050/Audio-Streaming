import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { ObjectId } from 'mongodb'
import { z } from 'zod'
import { entitlementFor } from '#helpers/entitlement.js'
import { MongoClientDeleteMany, MongoClientFindOne, MongoClientUpdateOne } from '#helpers/mongoclient.js'
import { successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { AuthVariables } from '#middleware/proxy.js'
import type { CoreUserDoc, SubscriptionDoc } from '#types/docs.js'

const tasteSchema = z.object({
  genres: z.array(z.string().min(1).max(40)).min(1).max(10),
  artist_ids: z.array(z.string().min(1).max(64)).max(20),
}).strict()

export const meRoutes = new Hono<{ Variables: AuthVariables }>()

meRoutes.get('/', async (c) => {
  const userId = c.get('userId')
  try {
    const [{ data: user }, entitlement] = await Promise.all([
      MongoClientFindOne<CoreUserDoc>('core_users', { _id: new ObjectId(userId) }),
      entitlementFor(userId, new Date()),
    ])
    if (!user) return handleError(c, 'me:get', 'user vanished after auth')
    return successResponse(c, {
      user: { id: userId, email: user.email, name: user.name, picture: user.picture, taste_genres: user.taste_genres, taste_artist_ids: user.taste_artist_ids },
      entitlement,
    })
  } catch (e) {
    return handleError(c, 'me:get', e)
  }
})

meRoutes.patch('/taste', zValidator('json', tasteSchema, validationHook), async (c) => {
  const { genres, artist_ids: artistIds } = c.req.valid('json')
  try {
    await MongoClientUpdateOne<CoreUserDoc>('core_users', { _id: new ObjectId(c.get('userId')) }, { $set: { taste_genres: genres, taste_artist_ids: artistIds } })
    return successResponse(c, { taste_genres: genres, taste_artist_ids: artistIds })
  } catch (e) {
    return handleError(c, 'me:taste', e)
  }
})

// Google Play account-deletion requirement. Idempotent. The user row goes last
// so a failed wipe leaves an account the user can retry deleting.
meRoutes.delete('/', async (c) => {
  const userId = c.get('userId')
  try {
    await Promise.all([
      MongoClientDeleteMany('library_items', { user_id: userId }),
      MongoClientDeleteMany('play_events', { user_id: userId }),
      MongoClientDeleteMany('catalog_requests', { user_id: userId }),
      MongoClientDeleteMany<SubscriptionDoc>('subscriptions', { _id: userId }),
    ])
    await MongoClientDeleteMany<CoreUserDoc>('core_users', { _id: new ObjectId(userId) })
    return successResponse(c, { deleted: true })
  } catch (e) {
    return handleError(c, 'me:delete', e)
  }
})
