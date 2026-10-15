import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getDatabase } from '#helpers/mongoclient.js'
import { acknowledgePlaySubscription, fetchPlaySubscription, type PlaySubscription } from '#helpers/play-developer-api.js'
import { api, json, newUserToken, wipeDb } from './helpers.js'

vi.mock('#helpers/play-developer-api.js', () => ({ fetchPlaySubscription: vi.fn(), acknowledgePlaySubscription: vi.fn() }))
const fetchPlay = vi.mocked(fetchPlaySubscription)
const acknowledge = vi.mocked(acknowledgePlaySubscription)

const inAMonth = () => new Date(Date.now() + 30 * 86_400_000).toISOString()
const playState = (accountId: string, overrides: Partial<PlaySubscription> = {}): PlaySubscription => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
  acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
  externalAccountIdentifiers: { obfuscatedExternalAccountId: accountId },
  lineItems: [{ productId: 'premium', expiryTime: inAMonth(), autoRenewingPlan: { autoRenewEnabled: true }, offerDetails: { basePlanId: 'monthly' } }],
  ...overrides,
})

const pubSub = (payload: object, messageId = 'm1') => JSON.stringify({
  message: { data: Buffer.from(JSON.stringify(payload)).toString('base64'), messageId, publishTime: new Date().toISOString() },
  subscription: 'projects/hires/subscriptions/rtdn',
})
const subscriptionNote = (purchaseToken: string) => ({ version: '1.0', packageName: 'com.anish1050.hires', eventTimeMillis: '1', subscriptionNotification: { version: '1.0', notificationType: 4, purchaseToken } })
const RTDN_URL = '/api/billing/google/rtdn?token=rtdn-test-secret'

describe('billing verify', () => {
  beforeEach(async () => {
    await wipeDb()
    vi.clearAllMocks()
  })

  it('activates premium for the purchasing account and acknowledges once', async () => {
    const { token, userId } = await newUserToken()
    fetchPlay.mockResolvedValue(playState(userId))
    const res = await api('/api/billing/google/verify', { method: 'POST', body: JSON.stringify({ purchase_token: 'token-abcdef' }) }, token)
    expect((await json(res)).data.tier).toBe('premium')
    expect(acknowledge).toHaveBeenCalledWith('premium', 'token-abcdef')
  })

  it('refuses a token bought by another account', async () => {
    const { token } = await newUserToken()
    fetchPlay.mockResolvedValue(playState('someone-else'))
    const res = await api('/api/billing/google/verify', { method: 'POST', body: JSON.stringify({ purchase_token: 'token-abcdef' }) }, token)
    expect(res.status).toBe(403)
    const db = await getDatabase()
    expect(await db.collection('subscriptions').countDocuments()).toBe(0)
  })

  it('does not acknowledge an already acknowledged purchase', async () => {
    const { token, userId } = await newUserToken()
    fetchPlay.mockResolvedValue(playState(userId, { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' }))
    await api('/api/billing/google/verify', { method: 'POST', body: JSON.stringify({ purchase_token: 'token-abcdef' }) }, token)
    expect(acknowledge).not.toHaveBeenCalled()
  })
})

describe('billing rtdn', () => {
  beforeEach(async () => {
    await wipeDb()
    vi.clearAllMocks()
  })

  it('rejects a wrong secret', async () => {
    expect((await api('/api/billing/google/rtdn?token=nope', { method: 'POST', body: pubSub(subscriptionNote('t')) })).status).toBe(401)
  })

  it('stores a test notification', async () => {
    const res = await api(RTDN_URL, { method: 'POST', body: pubSub({ version: '1.0', testNotification: { version: '1.0' } }) })
    expect((await json(res)).data.test).toBe(true)
  })

  it('updates the subscription from Play state', async () => {
    const { userId } = await newUserToken()
    fetchPlay.mockResolvedValue(playState(userId, { subscriptionState: 'SUBSCRIPTION_STATE_CANCELED' }))
    await api(RTDN_URL, { method: 'POST', body: pubSub(subscriptionNote('token-xyz')) })
    const db = await getDatabase()
    expect((await db.collection('subscriptions').findOne({ _id: userId as never }))?.state).toBe('SUBSCRIPTION_STATE_CANCELED')
  })

  it('handles a redelivered message only once', async () => {
    const { userId } = await newUserToken()
    fetchPlay.mockResolvedValue(playState(userId))
    await api(RTDN_URL, { method: 'POST', body: pubSub(subscriptionNote('token-xyz'), 'same') })
    const second = await api(RTDN_URL, { method: 'POST', body: pubSub(subscriptionNote('token-xyz'), 'same') })
    expect((await json(second)).data.duplicate).toBe(true)
    expect(fetchPlay).toHaveBeenCalledTimes(1)
  })

  it('returns 500 on failure, then processes the retry', async () => {
    const { userId } = await newUserToken()
    fetchPlay.mockRejectedValueOnce(new Error('play down')).mockResolvedValueOnce(playState(userId))
    expect((await api(RTDN_URL, { method: 'POST', body: pubSub(subscriptionNote('token-xyz'), 'retry') })).status).toBe(500)
    const db = await getDatabase()
    expect((await db.collection('billing_events').findOne({ _id: 'retry' as never }))?.error).toBe('play down')
    const retry = await api(RTDN_URL, { method: 'POST', body: pubSub(subscriptionNote('token-xyz'), 'retry') })
    expect((await json(retry)).data.processed).toBe(true)
  })

  it('rejects a payload that is not valid JSON with 400 so Pub/Sub stops retrying', async () => {
    const body = JSON.stringify({ message: { data: Buffer.from('not json').toString('base64'), messageId: 'bad' } })
    expect((await api(RTDN_URL, { method: 'POST', body })).status).toBe(400)
  })
})
