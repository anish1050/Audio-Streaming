import { MongoClient, ServerApiVersion } from 'mongodb'

const options = {
  maxPoolSize: 10,
  minPoolSize: 1,
  maxIdleTimeMS: 120000,
  connectTimeoutMS: 30000,
  socketTimeoutMS: 60000,
  serverSelectionTimeoutMS: 30000,
  serverApi: { version: ServerApiVersion.v1, strict: false, deprecationErrors: true },
  retryWrites: true,
  retryReads: true,
}

let cachedClient: MongoClient | null = null
let cachedClientPromise: Promise<MongoClient> | null = null

export async function getClientPromise(): Promise<MongoClient> {
  if (cachedClientPromise) return cachedClientPromise

  const uri = process.env.MONGODB_URI
  if (!uri) throw new Error('MONGODB_URI environment variable is required but was not set.')

  if (!cachedClient) cachedClient = new MongoClient(uri, options)
  const promise = cachedClient.connect()
  // Clear both caches on failure so the next request retries instead of
  // receiving the same permanently-rejected promise.
  promise.catch(() => {
    cachedClientPromise = null
    cachedClient = null
  })
  cachedClientPromise = promise
  return promise
}

export async function closeClient(): Promise<void> {
  await cachedClient?.close()
  cachedClient = null
  cachedClientPromise = null
}
