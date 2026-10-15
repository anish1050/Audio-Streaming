import { SignJWT, jwtVerify, createRemoteJWKSet, errors } from 'jose'
import { requireEnv } from './env.js'

const GOOGLE_JWKS = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'))
const GOOGLE_ISSUERS = ['https://accounts.google.com', 'accounts.google.com']

const appSecret = () => new TextEncoder().encode(requireEnv('JWT_SECRET'))

export interface GoogleIdentity {
  sub: string
  email: string
  name: string
  picture: string
}

/** The token itself is bad (expired, wrong audience, bad signature): a 401, not a 500. */
export class GoogleTokenError extends Error {}

export async function verifyGoogleIdToken(idToken: string): Promise<GoogleIdentity> {
  const audience = requireEnv('GOOGLE_CLIENT_ID')
  let payload
  try {
    payload = (await jwtVerify(idToken, GOOGLE_JWKS, { audience, issuer: GOOGLE_ISSUERS })).payload
  } catch (e) {
    if (e instanceof errors.JOSEError && !(e instanceof errors.JWKSTimeout)) throw new GoogleTokenError(e.message)
    throw e
  }
  if (!payload.sub) throw new GoogleTokenError('Google token has no subject')
  return { sub: payload.sub, email: String(payload.email ?? ''), name: String(payload.name ?? ''), picture: String(payload.picture ?? '') }
}

export function signAppJwt(userId: string): Promise<string> {
  return new SignJWT({}).setProtectedHeader({ alg: 'HS256' }).setSubject(userId).setIssuedAt().setExpirationTime('30d').sign(appSecret())
}

// Returns null only for a bad token; a missing JWT_SECRET still throws so a
// broken deploy fails loudly instead of logging everyone out.
export async function verifyAppJwt(token: string): Promise<string | null> {
  const secret = appSecret()
  try {
    const { payload } = await jwtVerify(token, secret, { algorithms: ['HS256'] })
    return payload.sub ?? null
  } catch {
    return null
  }
}
