import { ObjectId } from 'mongodb'
import app from '../src/index.js'
import { signAppJwt } from '#helpers/jwt.js'
import { getDatabase } from '#helpers/mongoclient.js'

export async function api(path: string, init: RequestInit = {}, token?: string) {
  const headers = new Headers(init.headers)
  if (typeof init.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return app.request(path, { ...init, headers })
}

// Response bodies in tests are asserted structurally, so a loose type is fine here.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type JsonBody = any

export function json(res: Response): Promise<JsonBody> {
  return res.json() as Promise<JsonBody>
}

export async function newUserToken(overrides: Record<string, unknown> = {}) {
  const id = new ObjectId()
  const db = await getDatabase()
  await db.collection('core_users').insertOne({
    _id: id, google_sub: `sub-${id}`, email: `${id}@example.com`, name: 'Test', picture: '', taste_genres: [], taste_artist_ids: [],
    created_at: new Date(), last_login_at: new Date(), ...overrides,
  })
  const userId = id.toString()
  return { userId, token: await signAppJwt(userId) }
}

export async function wipeDb() {
  const db = await getDatabase()
  const collections = await db.collections()
  await Promise.all(collections.map((c) => c.deleteMany({})))
}
