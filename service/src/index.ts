import { Hono } from 'hono'
import { logger } from 'hono/logger'
import { trimTrailingSlash } from 'hono/trailing-slash'
import { ensureIndexes } from '#helpers/indexes.js'
import { proxyMiddleware, type AuthVariables } from '#middleware/proxy.js'

const app = new Hono<{ Variables: AuthVariables }>()

app.use(trimTrailingSlash())
// Logs method, path, status and time only; never bodies or tokens.
if (process.env.NODE_ENV !== 'test') app.use('*', logger())
app.use('*', async (_c, next) => {
  await ensureIndexes()
  await next()
})
app.use('*', proxyMiddleware)

app.get('/api/health', (c) => c.json({ success: true, data: { ok: true } }))

app.onError((err, c) => {
  console.error('[unhandled]', err.message)
  return c.json({ success: false, error: 'Internal Server Error' }, 500)
})
app.notFound((c) => c.json({ success: false, error: 'Not Found' }, 404))

export default app
