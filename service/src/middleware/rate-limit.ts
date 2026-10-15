import type { MiddlewareHandler } from 'hono'
import { errorResponse } from '#helpers/response.js'
import type { AuthVariables } from './proxy.js'

// ponytail: in-memory sliding window per Cloud Run instance. Move to a Mongo
// TTL collection if several instances make the limits leaky in practice.
const buckets = new Map<string, number[]>()

export function rateLimit(max: number, windowSec: number, bucket: string): MiddlewareHandler<{ Variables: AuthVariables }> {
  return async (c, next) => {
    const who = c.get('userId') || c.req.header('x-forwarded-for')?.split(',')[0]?.trim() || 'anon'
    const key = `${bucket}:${who}`
    const now = Date.now()
    const windowStart = now - windowSec * 1000
    const hits = (buckets.get(key) ?? []).filter((t) => t > windowStart)
    if (hits.length >= max) {
      c.header('Retry-After', String(windowSec))
      return errorResponse(c, 'Too many requests', 429)
    }
    hits.push(now)
    buckets.set(key, hits)
    await next()
  }
}
