import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { MongoClientFindOneAndUpdate } from '#helpers/mongoclient.js'
import { GoogleTokenError, signAppJwt, verifyGoogleIdToken } from '#helpers/jwt.js'
import { errorResponse, successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { CoreUserDoc } from '#types/docs.js'

const bodySchema = z.object({ id_token: z.string().min(20) }).strict()

export const authRoutes = new Hono()

authRoutes.post('/google', zValidator('json', bodySchema, validationHook), async (c) => {
  const { id_token: idToken } = c.req.valid('json')

  let identity
  try {
    identity = await verifyGoogleIdToken(idToken)
  } catch (e) {
    // Only a token the user controls is a 401; our own config or Google's JWKS
    // being unreachable is a 500 so the misconfiguration is not hidden.
    if (e instanceof GoogleTokenError) return errorResponse(c, 'Invalid Google token', 401)
    return handleError(c, 'auth:google', e)
  }

  try {
    const now = new Date()
    const { data: user } = await MongoClientFindOneAndUpdate<CoreUserDoc>(
      'core_users',
      { google_sub: identity.sub },
      {
        $setOnInsert: { google_sub: identity.sub, created_at: now, taste_genres: [], taste_artist_ids: [] },
        $set: { email: identity.email, name: identity.name, picture: identity.picture, last_login_at: now },
      },
      { upsert: true, returnDocument: 'after' },
    )
    if (!user) return handleError(c, 'auth:google', 'upsert returned no user')
    const userId = user._id.toString()
    return successResponse(c, { access_token: await signAppJwt(userId), user: { id: userId, email: user.email, name: user.name, picture: user.picture } })
  } catch (e) {
    return handleError(c, 'auth:google', e)
  }
})
