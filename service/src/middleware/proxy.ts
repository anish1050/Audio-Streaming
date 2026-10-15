import type { MiddlewareHandler } from 'hono'
import { verifyAppJwt } from '#helpers/jwt.js'

export type AuthVariables = { userId: string }

// Resolves userId from a bearer token on every request; empty when absent or
// invalid. Route groups that require auth mount authMiddleware on top.
export const proxyMiddleware: MiddlewareHandler<{ Variables: AuthVariables }> = async (c, next) => {
  const header = c.req.header('Authorization') ?? ''
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : ''
  c.set('userId', token ? ((await verifyAppJwt(token)) ?? '') : '')
  await next()
}
