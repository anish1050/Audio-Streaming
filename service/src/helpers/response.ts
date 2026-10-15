import type { Context } from 'hono'

export function successResponse<T>(c: Context, data: T, status: 200 | 201 = 200) {
  return c.json({ success: true, data }, status)
}

export function errorResponse(c: Context, msg: string, status: 400 | 401 | 403 | 404 | 409 | 413 | 429 | 500 = 500) {
  return c.json({ success: false, error: msg }, status)
}
