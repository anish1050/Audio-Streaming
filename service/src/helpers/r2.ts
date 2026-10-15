import { GetObjectCommand, S3Client } from '@aws-sdk/client-s3'
import { getSignedUrl } from '@aws-sdk/s3-request-presigner'
import { requireEnv } from './env.js'

let client: S3Client | undefined

export function r2Client(): S3Client {
  client ??= new S3Client({
    region: 'auto',
    endpoint: `https://${requireEnv('R2_ACCOUNT_ID')}.r2.cloudflarestorage.com`,
    credentials: { accessKeyId: requireEnv('R2_ACCESS_KEY_ID'), secretAccessKey: requireEnv('R2_SECRET_ACCESS_KEY') },
  })
  return client
}

// Signing is local HMAC work; no network call happens here.
export function presignGet(key: string, expiresInSeconds: number): Promise<string> {
  return getSignedUrl(r2Client(), new GetObjectCommand({ Bucket: requireEnv('R2_BUCKET'), Key: key }), { expiresIn: expiresInSeconds })
}
