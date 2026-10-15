import { beforeEach, describe, expect, it } from 'vitest'
import { getDatabase } from '#helpers/mongoclient.js'
import { api, json, newUserToken, wipeDb } from './helpers.js'
import { seedCatalog } from './fixtures.js'

async function premiumToken() {
  const user = await newUserToken()
  const db = await getDatabase()
  await db.collection('subscriptions').insertOne({ _id: user.userId as never, state: 'SUBSCRIPTION_STATE_ACTIVE', expires_at: new Date(Date.now() + 86_400_000), verified_at: new Date() })
  return user.token
}

describe('audio urls', () => {
  beforeEach(async () => {
    await wipeDb()
    await seedCatalog()
  })

  it('gives premium users hi-res with a presigned R2 url', async () => {
    const body = await json(await api('/api/tracks/track-midnight/stream?quality=hires', {}, await premiumToken()))
    expect(body.data.quality).toBe('hires')
    expect(body.data.sample_rate).toBe(96000)
    expect(body.data.url).toContain('r2.cloudflarestorage.com')
    expect(body.data.url).toContain('X-Amz-Expires=900')
  })

  it('falls back to the next tier when hi-res does not exist', async () => {
    const body = await json(await api('/api/tracks/track-cd/stream?quality=hires', {}, await premiumToken()))
    expect(body.data.quality).toBe('lossless')
  })

  it('gives free users a preview', async () => {
    const { token } = await newUserToken()
    const body = await json(await api('/api/tracks/track-midnight/stream?quality=hires', {}, token))
    expect(body.data.quality).toBe('preview')
  })

  it('blocks downloads for free users', async () => {
    const { token } = await newUserToken()
    expect((await api('/api/tracks/track-midnight/download?quality=hires', {}, token)).status).toBe(403)
  })

  it('gives premium users a 6 hour download url', async () => {
    const body = await json(await api('/api/tracks/track-midnight/download?quality=lossless', {}, await premiumToken()))
    expect(body.data.url).toContain('X-Amz-Expires=21600')
  })

  it('rejects an unknown quality', async () => {
    const { token } = await newUserToken()
    expect((await api('/api/tracks/track-midnight/stream?quality=ultra', {}, token)).status).toBe(400)
  })

  it('redirects public image urls to R2', async () => {
    const res = await api('/api/images/albums/album-night')
    expect(res.status).toBe(302)
    expect(res.headers.get('location')).toContain('albums/album-night.jpg')
  })
})
