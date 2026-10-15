import type { MiddlewareHandler } from 'hono'
import { ObjectId } from 'mongodb'
import { MongoClientFindOne } from '#helpers/mongoclient.js'
import { errorResponse } from '#helpers/response.js'
import type { CoreUserDoc } from '#types/docs.js'
import type { AuthVariables } from './proxy.js'

// A valid signature is not enough: a token outlives DELETE /api/me by up to 30
// days, so every authed request checks the account still exists.
export const authMiddleware: MiddlewareHandler<{ Variables: AuthVariables }> = async (c, next) => {
  const userId = c.get('userId')
  if (!userId || !ObjectId.isValid(userId)) return errorResponse(c, 'Unauthorized', 401)
  const user = await MongoClientFindOne<CoreUserDoc>('core_users', { _id: new ObjectId(userId) }, { projection: { _id: 1 } })
  if (!user.data) return errorResponse(c, 'Account no longer exists', 401)
  await next()
}
