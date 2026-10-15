import { Hono } from 'hono'
import { zValidator } from '@hono/zod-validator'
import { z } from 'zod'
import { albumRef, artistRef, loadTrackViews } from '#helpers/catalog-view.js'
import { MongoClientDeleteMany, MongoClientFind, MongoClientUpdateOne } from '#helpers/mongoclient.js'
import { successResponse } from '#helpers/response.js'
import { handleError, validationHook } from '#helpers/route.js'
import type { AuthVariables } from '#middleware/proxy.js'
import { LIBRARY_KINDS, type AlbumDoc, type ArtistDoc, type LibraryItemDoc, type LibraryKind, type PlaylistDoc } from '#types/docs.js'

const kindSchema = z.enum(LIBRARY_KINDS)
const listSchema = z.object({ kind: kindSchema }).strict()
const saveSchema = z.object({ kind: kindSchema, item_id: z.string().min(1).max(64) }).strict()
const removeSchema = z.object({ kind: kindSchema, id: z.string().min(1).max(64) })

export const libraryRoutes = new Hono<{ Variables: AuthVariables }>()

libraryRoutes.get('/', zValidator('query', listSchema, validationHook), async (c) => {
  const { kind } = c.req.valid('query')
  try {
    const { data: saved } = await MongoClientFind<LibraryItemDoc>('library_items', { user_id: c.get('userId'), kind }, { sort: { created_at: -1 } })
    return successResponse(c, { kind, items: await loadItems(kind, saved.map((s) => s.item_id)) })
  } catch (e) {
    return handleError(c, 'library:list', e)
  }
})

libraryRoutes.post('/', zValidator('json', saveSchema, validationHook), async (c) => {
  const { kind, item_id: itemId } = c.req.valid('json')
  const userId = c.get('userId')
  try {
    await MongoClientUpdateOne<LibraryItemDoc>('library_items', { user_id: userId, kind, item_id: itemId }, { $setOnInsert: { created_at: new Date() } }, { upsert: true })
    return successResponse(c, { kind, item_id: itemId, saved: true })
  } catch (e) {
    return handleError(c, 'library:save', e)
  }
})

libraryRoutes.delete('/:kind/:id', zValidator('param', removeSchema, validationHook), async (c) => {
  const { kind, id } = c.req.valid('param')
  try {
    await MongoClientDeleteMany<LibraryItemDoc>('library_items', { user_id: c.get('userId'), kind, item_id: id })
    return successResponse(c, { kind, item_id: id, saved: false })
  } catch (e) {
    return handleError(c, 'library:remove', e)
  }
})

async function loadItems(kind: LibraryKind, ids: string[]) {
  const inOrder = <T extends { _id: string }>(docs: T[]) => {
    const byId = new Map(docs.map((d) => [d._id, d]))
    return ids.flatMap((id) => byId.get(id) ?? [])
  }
  switch (kind) {
    case 'track':
      return loadTrackViews(ids)
    case 'album':
      return inOrder((await MongoClientFind<AlbumDoc>('catalog_albums', { _id: { $in: ids } })).data).map(albumRef)
    case 'artist':
      return inOrder((await MongoClientFind<ArtistDoc>('catalog_artists', { _id: { $in: ids } })).data).map(artistRef)
    case 'playlist':
      return inOrder((await MongoClientFind<PlaylistDoc>('catalog_playlists', { _id: { $in: ids } })).data).map((p) => ({ id: p._id, title: p.title, subtitle: p.subtitle }))
  }
}
