import { SignJWT, importPKCS8 } from 'jose'
import { requireEnv } from './env.js'

// The fields of a Play subscriptionsv2 purchase we act on.
export interface PlaySubscription {
  subscriptionState: string
  acknowledgementState?: 'ACKNOWLEDGEMENT_STATE_PENDING' | 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED'
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string }
  lineItems?: Array<{ productId: string; expiryTime: string; autoRenewingPlan?: { autoRenewEnabled?: boolean }; offerDetails?: { basePlanId?: string } }>
}

let cachedToken: { value: string; expiresAt: number } | undefined

async function accessToken(): Promise<string> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value
  const account = JSON.parse(requireEnv('PLAY_SERVICE_ACCOUNT_JSON')) as { client_email: string; private_key: string }
  const assertion = await new SignJWT({ scope: 'https://www.googleapis.com/auth/androidpublisher' })
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuer(account.client_email)
    .setAudience('https://oauth2.googleapis.com/token')
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(await importPKCS8(account.private_key, 'RS256'))
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  })
  if (!res.ok) throw new Error(`[play:token] ${res.status}`)
  const body = (await res.json()) as { access_token: string; expires_in: number }
  cachedToken = { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 }
  return cachedToken.value
}

const applicationUrl = () => `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${requireEnv('PLAY_PACKAGE_NAME')}`

export async function fetchPlaySubscription(purchaseToken: string): Promise<PlaySubscription> {
  const res = await fetch(`${applicationUrl()}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`, {
    headers: { authorization: `Bearer ${await accessToken()}` },
  })
  if (!res.ok) throw new Error(`[play:get] ${res.status}`)
  return (await res.json()) as PlaySubscription
}

export async function acknowledgePlaySubscription(productId: string, purchaseToken: string): Promise<void> {
  const res = await fetch(`${applicationUrl()}/purchases/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`, {
    method: 'POST',
    headers: { authorization: `Bearer ${await accessToken()}` },
  })
  if (!res.ok) throw new Error(`[play:ack] ${res.status}`)
}
