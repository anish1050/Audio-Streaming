import { MongoMemoryServer } from 'mongodb-memory-server'

let mongod: MongoMemoryServer

export async function setup() {
  mongod = await MongoMemoryServer.create()
  process.env.MONGODB_URI = mongod.getUri()
  process.env.MONGODB_DBNAME = 'hires_test'
  process.env.JWT_SECRET = 'test-secret-test-secret-test-secret-test-secret'
  process.env.GOOGLE_CLIENT_ID = 'test-client-id.apps.googleusercontent.com'
  process.env.R2_ACCOUNT_ID = 'testaccount'
  process.env.R2_ACCESS_KEY_ID = 'test-access-key'
  process.env.R2_SECRET_ACCESS_KEY = 'test-secret-key'
  process.env.R2_BUCKET = 'hires-test'
  process.env.PLAY_PACKAGE_NAME = 'com.anish1050.hires'
  process.env.RTDN_SECRET = 'rtdn-test-secret'
  process.env.CRON_SECRET = 'cron-test-secret'
  process.env.NODE_ENV = 'test'
}

export async function teardown() {
  await mongod.stop()
}
