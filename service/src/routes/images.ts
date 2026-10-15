import { Hono } from 'hono'
import { presignGet } from '#helpers/r2.js'
import { errorResponse } from '#helpers/response.js'
import { handleError } from '#helpers/route.js'

const IMAGE_TTL_SECONDS = 6 * 60 * 60
const ID_PATTERN = /^[a-z0-9-]{1,64}$/

// Public: cover art only. A stable URL that redirects to a short-lived R2 URL,
// so clients can cache by this URL while the bucket stays private.
export const imageRoutes = new Hono()

imageRoutes.get('/:kind/:id', async (c) => {
  const kind = c.req.param('kind')
  const id = c.req.param('id')
  if ((kind !== 'albums' && kind !== 'artists') || !ID_PATTERN.test(id)) return errorResponse(c, 'Image not found', 404)
  try {
    c.header('Cache-Control', 'public, max-age=3600')
    return c.redirect(await presignGet(`${kind}/${id}.jpg`, IMAGE_TTL_SECONDS), 302)
  } catch (e) {
    return handleError(c, 'images', e)
  }
})
