// Usage: npm run ingest:album -- --dir ./drops/<artist>/<album>
//
// The folder holds album.json, cover.jpg, optional artist.jpg and the masters
// (FLAC, or DSD .dsf/.dff). Re-running on the same folder updates in place.
import { mkdir, readFile, stat } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { PutObjectCommand } from '@aws-sdk/client-s3'
import { z } from 'zod'
import { requireEnv } from '#helpers/env.js'
import { landmarks } from '#helpers/fingerprint.js'
import { ensureIndexes } from '#helpers/indexes.js'
import { getDatabase } from '#helpers/mongoclient.js'
import { closeClient } from '#helpers/mongodb.js'
import { r2Client } from '#helpers/r2.js'
import { QUALITIES, type AlbumDoc, type ArtistDoc, type AssetInfo, type CounterDoc, type FingerprintDoc, type Quality, type TrackDoc } from '#types/docs.js'
import { DSD_CODECS, TIER_EXTENSION, decodeForFingerprint, ffmpeg, isAcceptedSource, measureLoudness, probe, tierArgs, tierFormat, type SourceInfo } from './ffmpeg.js'

const slug = z.string().regex(/^[a-z0-9-]{1,48}$/, 'lowercase letters, digits and dashes')
const albumFileSchema = z.object({
  rights_holder_id: z.string().min(1),
  artist: z.object({ id: slug, name: z.string().min(1), bio: z.string().default('') }).strict(),
  album: z.object({ id: slug, title: z.string().min(1), year: z.number().int(), upc: z.string().default(''), genres: z.array(z.string()).default([]) }).strict(),
  tracks: z.array(z.object({
    file: z.string().min(1),
    title: z.string().min(1),
    isrc: z.string().default(''),
    explicit: z.boolean().default(false),
    free_full_length: z.boolean().default(false),
    genres: z.array(z.string()).default([]),
    licence: z.string().default('direct'),
    attribution: z.string().default(''),
  }).strict()).min(1),
}).strict()
type AlbumFile = z.infer<typeof albumFileSchema>

const CONTENT_TYPE = { flac: 'audio/flac', opus: 'audio/ogg' } as const
const FINGERPRINT_BATCH = 10_000

async function main() {
  const { values } = parseArgs({ options: { dir: { type: 'string' } } })
  if (!values.dir) throw new Error('--dir is required')
  const dir = resolve(values.dir)
  const albumFile = albumFileSchema.parse(JSON.parse(await readFile(join(dir, 'album.json'), 'utf8')))

  await ensureIndexes()
  await upsertArtistAndAlbum(dir, albumFile)
  for (const [index, track] of albumFile.tracks.entries()) {
    const trackId = `${albumFile.album.id}-${String(index + 1).padStart(2, '0')}`
    await ingestTrack(dir, albumFile, track, trackId, index + 1)
    console.log(`ingested ${trackId} ${track.title}`)
  }
}

async function upsertArtistAndAlbum(dir: string, albumFile: AlbumFile) {
  const db = await getDatabase()
  const { artist, album } = albumFile
  const now = new Date()
  await uploadFile(`albums/${album.id}.jpg`, join(dir, 'cover.jpg'), 'image/jpeg')
  if (await exists(join(dir, 'artist.jpg'))) await uploadFile(`artists/${artist.id}.jpg`, join(dir, 'artist.jpg'), 'image/jpeg')
  await db.collection<ArtistDoc>('catalog_artists').updateOne(
    { _id: artist.id },
    { $set: { name: artist.name, slug: artist.id, bio: artist.bio, rights_holder_id: albumFile.rights_holder_id }, $setOnInsert: { created_at: now } },
    { upsert: true },
  )
  await db.collection<AlbumDoc>('catalog_albums').updateOne(
    { _id: album.id },
    { $set: { artist_id: artist.id, title: album.title, year: album.year, upc: album.upc, genres: album.genres }, $setOnInsert: { created_at: now } },
    { upsert: true },
  )
}

async function ingestTrack(dir: string, albumFile: AlbumFile, track: AlbumFile['tracks'][number], trackId: string, trackNo: number) {
  const source = await probe(join(dir, track.file))
  if (!isAcceptedSource(source)) throw new Error(`${track.file}: ${source.codec} ${source.bitDepth}-bit ${source.sampleRate} Hz is not a lossless master`)

  // Kept after ingest: embed.py reads lossless.flac from here.
  const workDir = join(dir, '..', '..', '.work', trackId)
  await mkdir(workDir, { recursive: true })
  const { master, masterInfo } = await prepareMaster(join(dir, track.file), source, workDir)

  const assets: Partial<Record<Quality, AssetInfo>> = {}
  for (const quality of QUALITIES) {
    const extension = TIER_EXTENSION[quality]
    const output = join(workDir, `${quality}.${extension}`)
    const args = tierArgs(quality, masterInfo, master, output)
    if (!args) continue
    await ffmpeg(args)
    const key = `audio/${trackId}/${quality}.${extension}`
    const bytes = (await stat(output)).size
    await uploadFile(key, output, CONTENT_TYPE[extension])
    const durationSeconds = quality === 'preview' ? 30 : masterInfo.durationMs / 1000
    const { sampleRate, bitDepth } = tierFormat(quality, masterInfo)
    assets[quality] = { key, codec: extension, sample_rate: sampleRate, bit_depth: bitDepth, bitrate_kbps: Math.round((bytes * 8) / durationSeconds / 1000), bytes }
  }

  const db = await getDatabase()
  const existing = await db.collection<TrackDoc>('catalog_tracks').findOne({ _id: trackId }, { projection: { fp_id: 1 } })
  const fpId = existing?.fp_id ?? (await nextFingerprintId())
  await db.collection<TrackDoc>('catalog_tracks').updateOne(
    { _id: trackId },
    {
      $set: {
        fp_id: fpId, album_id: albumFile.album.id, artist_id: albumFile.artist.id, title: track.title, track_no: trackNo, duration_ms: masterInfo.durationMs,
        isrc: track.isrc, explicit: track.explicit, genres: track.genres.length > 0 ? track.genres : albumFile.album.genres, loudness_lufs: await measureLoudness(master),
        free_full_length: track.free_full_length, assets, licence: track.licence, attribution: track.attribution, rights_holder_id: albumFile.rights_holder_id,
      },
      $setOnInsert: { embedding: [], created_at: new Date() },
    },
    { upsert: true },
  )
  await storeFingerprints(fpId, master)
}

/** DSD cannot be cut into tiers directly: convert it to 24/176.4 FLAC first. */
async function prepareMaster(file: string, source: SourceInfo, workDir: string): Promise<{ master: string; masterInfo: SourceInfo }> {
  if (!DSD_CODECS.has(source.codec)) return { master: file, masterInfo: source }
  const master = join(workDir, 'master.flac')
  await ffmpeg(['-hide_banner', '-loglevel', 'error', '-y', '-i', file, '-af', 'aresample=176400:resampler=soxr', '-c:a', 'flac', '-sample_fmt', 's32', '-bits_per_raw_sample', '24', master])
  return { master, masterInfo: { codec: 'flac', sampleRate: 176400, bitDepth: 24, durationMs: source.durationMs } }
}

async function storeFingerprints(fpId: number, master: string) {
  const db = await getDatabase()
  const rows: FingerprintDoc[] = landmarks(await decodeForFingerprint(master)).map((l) => ({ h: l.h, t: l.t, tid: fpId }))
  await db.collection<FingerprintDoc>('fingerprints').deleteMany({ tid: fpId })
  for (let i = 0; i < rows.length; i += FINGERPRINT_BATCH) {
    await db.collection<FingerprintDoc>('fingerprints').insertMany(rows.slice(i, i + FINGERPRINT_BATCH))
  }
}

async function nextFingerprintId(): Promise<number> {
  const db = await getDatabase()
  const counter = await db.collection<CounterDoc>('counters').findOneAndUpdate({ _id: 'fp_id' }, { $inc: { seq: 1 } }, { upsert: true, returnDocument: 'after' })
  if (!counter) throw new Error('fp_id counter missing')
  return counter.seq
}

async function uploadFile(key: string, file: string, contentType: string) {
  await r2Client().send(new PutObjectCommand({ Bucket: requireEnv('R2_BUCKET'), Key: key, Body: await readFile(file), ContentType: contentType }))
}

async function exists(file: string): Promise<boolean> {
  return stat(file).then(() => true, () => false)
}

main()
  .catch((e: unknown) => {
    console.error(e instanceof Error ? e.message : e)
    process.exitCode = 1
  })
  .finally(() => closeClient())
