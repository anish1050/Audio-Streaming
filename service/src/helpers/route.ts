import type { Context } from 'hono'
import type { ZodError } from 'zod'
import { errorResponse } from './response.js'

type HookResult = { success: true } | { success: false; error: ZodError }

// Shared zValidator hook: surfaces Zod's own message instead of a generic one.
export function validationHook(result: HookResult, c: Context) {
  if (!result.success) {
    return errorResponse(c, result.error.issues.map((i) => `${i.path.join('.') || 'body'}: ${i.message}`).join(', '), 400)
  }
  return undefined
}

export function handleError(c: Context, module: string, e: unknown) {
  const message = e instanceof Error ? e.message : String(e)
  console.error(`[${module}] ${message}`)
  return errorResponse(c, 'Internal Server Error', 500)
}
