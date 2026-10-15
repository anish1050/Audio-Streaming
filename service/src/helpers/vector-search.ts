import { MongoClientAggregate } from './mongoclient.js'

// Atlas Vector Search only exists on Atlas, so tests mock this module.
export async function findNearestTrackIds(queryVector: number[], limit: number): Promise<string[]> {
  const { data } = await MongoClientAggregate<{ _id: string }>('catalog_tracks', [
    { $vectorSearch: { index: 'track_embedding', path: 'embedding', queryVector, numCandidates: limit * 3, limit } },
    { $project: { _id: 1 } },
  ])
  return data.map((d) => d._id)
}
