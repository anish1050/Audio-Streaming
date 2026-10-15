// Usage: npm run db:indexes
import { ensureIndexes } from '#helpers/indexes.js'
import { closeClient } from '#helpers/mongodb.js'

ensureIndexes()
  .then(() => console.log('indexes ready'))
  .catch((e: unknown) => {
    console.error(e instanceof Error ? e.message : e)
    process.exitCode = 1
  })
  .finally(() => closeClient())
