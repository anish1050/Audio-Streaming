import { Hono } from 'hono'
import { logger } from 'hono/logger'
import { trimTrailingSlash } from 'hono/trailing-slash'
import { ensureIndexes } from '#helpers/indexes.js'
import { authMiddleware } from '#middleware/auth.js'
import { proxyMiddleware, type AuthVariables } from '#middleware/proxy.js'
import { rateLimit } from '#middleware/rate-limit.js'
import { audioRoutes } from '#routes/audio.js'
import { authRoutes } from '#routes/auth.js'
import { catalogRoutes } from '#routes/catalog.js'
import { imageRoutes } from '#routes/images.js'
import { meRoutes } from '#routes/me.js'

// Everything under /api needs a signed-in user except these prefixes.
const PUBLIC_PREFIXES = ['/api/health', '/api/auth/', '/api/images/', '/api/billing/google/rtdn', '/api/jobs/', '/api/credits']
const isPublicPath = (path: string) => PUBLIC_PREFIXES.some((prefix) => path.startsWith(prefix))

const app = new Hono<{ Variables: AuthVariables }>()

app.use(trimTrailingSlash())
// Logs method, path, status and time only; never bodies or tokens.
if (process.env.NODE_ENV !== 'test') app.use('*', logger())
app.use('*', async (_c, next) => {
  await ensureIndexes()
  await next()
})
app.use('*', proxyMiddleware)
app.use('/api/*', (c, next) => (isPublicPath(c.req.path) ? next() : authMiddleware(c, next)))

app.use('/api/auth/*', rateLimit(10, 60, 'auth'))
app.use('/api/*', (c, next) => (isPublicPath(c.req.path) ? next() : rateLimit(120, 60, 'api')(c, next)))

app.get('/api/health', (c) => c.json({ success: true, data: { ok: true } }))

app.route('/api/auth', authRoutes)
app.route('/api/images', imageRoutes)
app.route('/api/me', meRoutes)
app.route('/api', catalogRoutes)
app.route('/api', audioRoutes)

app.onError((err, c) => {
  console.error('[unhandled]', err.message)
  return c.json({ success: false, error: 'Internal Server Error' }, 500)
})
app.notFound((c) => c.json({ success: false, error: 'Not Found' }, 404))

export default app
