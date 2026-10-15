import { getClientPromise } from './mongodb.js'
import type {
  AggregateOptions,
  AnyBulkWriteOperation,
  BulkWriteResult,
  Db,
  DeleteResult,
  Document,
  Filter,
  FindOneAndUpdateOptions,
  FindOptions,
  InsertOneResult,
  OptionalId,
  OptionalUnlessRequiredId,
  UpdateFilter,
  UpdateOptions,
  UpdateResult,
} from 'mongodb'

export async function getDatabase(): Promise<Db> {
  const dbName = process.env.MONGODB_DBNAME
  if (!dbName) throw new Error('MONGODB_DBNAME environment variable is required but was not set.')
  const client = await getClientPromise()
  return client.db(dbName)
}

// `status` means "did the operation find or change something". Driver and
// network errors are thrown, never folded into an empty result, so a route
// cannot mistake an outage for "no records".
export interface MongoOperationResult<T> {
  status: boolean
  data: T
}

export const MongoClientFindOne = async <T extends Document>(collection: string, query: Filter<T>, options: FindOptions = {}): Promise<MongoOperationResult<T | null>> => {
  const db = await getDatabase()
  const data = (await db.collection<T>(collection).findOne(query, options)) as T | null
  return { status: !!data, data }
}

export const MongoClientFind = async <T extends Document>(collection: string, query: Filter<T>, options: FindOptions = {}): Promise<MongoOperationResult<T[]>> => {
  const db = await getDatabase()
  const data = (await db.collection<T>(collection).find(query, options).toArray()) as unknown as T[]
  return { status: data.length > 0, data }
}

export const MongoClientAggregate = async <T extends Document>(collection: string, pipeline: Document[], options: AggregateOptions = {}): Promise<MongoOperationResult<T[]>> => {
  const db = await getDatabase()
  const data = await db.collection(collection).aggregate<T>(pipeline, options).toArray()
  return { status: data.length > 0, data }
}

export const MongoClientInsertOne = async <T extends Document>(collection: string, document: OptionalId<T>): Promise<MongoOperationResult<InsertOneResult<T>>> => {
  const db = await getDatabase()
  const result = await db.collection<T>(collection).insertOne(document as OptionalUnlessRequiredId<T>)
  return { status: result.acknowledged, data: result }
}

// Unordered insert where duplicate keys are expected (idempotent client
// retries). Returns how many were new; any other error is thrown.
export const MongoClientInsertManyIgnoringDuplicates = async <T extends Document>(collection: string, documents: OptionalId<T>[]): Promise<number> => {
  if (documents.length === 0) return 0
  const db = await getDatabase()
  try {
    const result = await db.collection<T>(collection).insertMany(documents as OptionalUnlessRequiredId<T>[], { ordered: false })
    return result.insertedCount
  } catch (e) {
    const writeErrors = (e as { writeErrors?: Array<{ code: number }> }).writeErrors ?? []
    if (writeErrors.length > 0 && writeErrors.every((w) => w.code === 11000)) return documents.length - writeErrors.length
    throw e
  }
}

export const MongoClientUpdateOne = async <T extends Document>(collection: string, query: Filter<T>, update: UpdateFilter<T>, options: UpdateOptions = {}): Promise<MongoOperationResult<UpdateResult>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  const db = await getDatabase()
  const result = await db.collection<T>(collection).updateOne(query, update, options)
  return { status: result.acknowledged, data: result }
}

export const MongoClientFindOneAndUpdate = async <T extends Document>(collection: string, query: Filter<T>, update: UpdateFilter<T>, options: FindOneAndUpdateOptions = {}): Promise<MongoOperationResult<T | null>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  const db = await getDatabase()
  const data = (await db.collection<T>(collection).findOneAndUpdate(query, update, options)) as T | null
  return { status: !!data, data }
}

export const MongoClientDeleteMany = async <T extends Document>(collection: string, query: Filter<T>): Promise<MongoOperationResult<DeleteResult>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  const db = await getDatabase()
  const result = await db.collection<T>(collection).deleteMany(query)
  if (!result.acknowledged) throw new Error(`deleteMany on ${collection} was not acknowledged`)
  return { status: result.deletedCount > 0, data: result }
}

export const MongoClientBulkWrite = async <T extends Document>(collection: string, operations: AnyBulkWriteOperation<T>[]): Promise<BulkWriteResult | null> => {
  if (operations.length === 0) return null
  const db = await getDatabase()
  return db.collection<T>(collection).bulkWrite(operations, { ordered: false })
}

export function isDuplicateKeyError(e: unknown): boolean {
  return (e as { code?: number }).code === 11000
}
