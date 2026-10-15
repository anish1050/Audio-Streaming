import { beforeEach, describe, expect, it } from 'vitest'
import { getDatabase } from '#helpers/mongoclient.js'
import { api, json, newUserToken, wipeDb } from './helpers.js'

describe('me', () => {
  beforeEach(wipeDb)

  it('rejects a missing token', async () => {
    expect((await api('/api/me')).status).toBe(401)
  })

  it('returns the user on the free tier without a subscription', async () => {
    const { token, userId } = await newUserToken()
    const body = await json(await api('/api/me', {}, token))
    expect(body.data.user.id).toBe(userId)
    expect(body.data.entitlement).toEqual({ tier: 'free', expires_at: null, verified_at: null })
  })

  it('returns premium for an active subscription', async () => {
    const { token, userId } = await newUserToken()
    const db = await getDatabase()
    await db.collection('subscriptions').insertOne({ _id: userId as never, state: 'SUBSCRIPTION_STATE_ACTIVE', expires_at: new Date(Date.now() + 86_400_000), verified_at: new Date() })
    const body = await json(await api('/api/me', {}, token))
    expect(body.data.entitlement.tier).toBe('premium')
  })

  it('saves onboarding taste', async () => {
    const { token } = await newUserToken()
    const res = await api('/api/me/taste', { method: 'PATCH', body: JSON.stringify({ genres: ['lo-fi'], artist_ids: ['artist-arjun'] }) }, token)
    expect(res.status).toBe(200)
    const body = await json(await api('/api/me', {}, token))
    expect(body.data.user.taste_genres).toEqual(['lo-fi'])
  })

  it('rejects unknown taste fields', async () => {
    const { token } = await newUserToken()
    const res = await api('/api/me/taste', { method: 'PATCH', body: JSON.stringify({ genres: ['pop'], artist_ids: [], extra: 1 }) }, token)
    expect(res.status).toBe(400)
  })

  it('deletes the account and its data, and the old token stops working', async () => {
    const { token, userId } = await newUserToken()
    const db = await getDatabase()
    await db.collection('play_events').insertOne({ _id: 'e1' as never, user_id: userId })
    expect((await api('/api/me', { method: 'DELETE' }, token)).status).toBe(200)
    expect(await db.collection('play_events').countDocuments({ user_id: userId })).toBe(0)
    expect((await api('/api/me', {}, token)).status).toBe(401)
  })
})
