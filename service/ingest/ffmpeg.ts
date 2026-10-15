import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import type { Quality } from '#types/docs.js'

const run = promisify(execFile)
const MAX_BUFFER = 1024 * 1024 * 1024

export interface SourceInfo {
  codec: string
  sampleRate: number
  bitDepth: number
  durationMs: number
}

export const DSD_CODECS = new Set(['dsd_lsbf', 'dsd_msbf', 'dsd_lsbf_planar', 'dsd_msbf_planar'])
const ACCEPTED_CODECS = new Set(['flac', ...DSD_CODECS])

export const TIER_EXTENSION: Record<Quality, 'flac' | 'opus'> = { hires: 'flac', lossless: 'flac', high: 'opus', low: 'opus', preview: 'opus' }

/** ffmpeg arguments for one delivery tier, or null when the tier would add nothing (hi-res from a CD-quality master). */
export function tierArgs(quality: Quality, source: SourceInfo, input: string, output: string): string[] | null {
  const base = ['-hide_banner', '-loglevel', 'error', '-y', '-i', input, '-map', '0:a:0', '-map_metadata', '-1']
  switch (quality) {
    case 'hires': {
      if (source.sampleRate <= 48000 && source.bitDepth <= 16) return null
      const sampleFormat = source.bitDepth > 16 ? ['-sample_fmt', 's32', '-bits_per_raw_sample', '24'] : ['-sample_fmt', 's16']
      return [...base, '-af', `aresample=${Math.min(source.sampleRate, 192000)}:resampler=soxr`, '-c:a', 'flac', ...sampleFormat, '-compression_level', '8', output]
    }
    case 'lossless':
      return [...base, '-af', 'aresample=44100:resampler=soxr:dither_method=triangular', '-c:a', 'flac', '-sample_fmt', 's16', '-compression_level', '8', output]
    case 'high':
      return [...base, '-c:a', 'libopus', '-b:a', '160k', output]
    case 'low':
      return [...base, '-c:a', 'libopus', '-b:a', '64k', output]
    case 'preview': {
      const startSeconds = Math.floor(source.durationMs / 4 / 1000)
      return ['-hide_banner', '-loglevel', 'error', '-y', '-ss', String(startSeconds), '-t', '30', '-i', input, '-map', '0:a:0', '-map_metadata', '-1', '-c:a', 'libopus', '-b:a', '96k', output]
    }
  }
}

/** Sample rate and bit depth a tier is delivered at. Opus has no bit depth: 0. */
export function tierFormat(quality: Quality, source: SourceInfo): { sampleRate: number; bitDepth: number } {
  if (quality === 'hires') return { sampleRate: Math.min(source.sampleRate, 192000), bitDepth: source.bitDepth > 16 ? 24 : 16 }
  if (quality === 'lossless') return { sampleRate: 44100, bitDepth: 16 }
  return { sampleRate: 48000, bitDepth: 0 }
}

interface FfprobeOutput {
  streams?: Array<{ codec_name?: string; sample_rate?: string; bits_per_raw_sample?: string; bits_per_sample?: number }>
  format?: { duration?: string }
}

export function parseProbe(json: string): SourceInfo {
  const probe = JSON.parse(json) as FfprobeOutput
  const stream = probe.streams?.[0]
  if (!stream?.codec_name) throw new Error('no audio stream')
  const bitDepth = Number(stream.bits_per_raw_sample) || stream.bits_per_sample || 0
  return {
    codec: stream.codec_name,
    sampleRate: Number(stream.sample_rate ?? 0),
    bitDepth: DSD_CODECS.has(stream.codec_name) ? 24 : bitDepth,
    durationMs: Math.round(Number(probe.format?.duration ?? 0) * 1000),
  }
}

/** Integrated loudness from the summary ffmpeg's ebur128 filter prints last. */
export function parseLoudness(stderr: string): number {
  const values = [...stderr.matchAll(/I:\s+(-?[\d.]+) LUFS/g)].map((m) => Number(m[1]))
  const integrated = values.at(-1)
  if (integrated === undefined || Number.isNaN(integrated)) throw new Error('ebur128 summary not found')
  return integrated
}

export function isAcceptedSource(source: SourceInfo): boolean {
  if (!ACCEPTED_CODECS.has(source.codec)) return false
  return DSD_CODECS.has(source.codec) || (source.bitDepth >= 16 && source.sampleRate >= 44100)
}

export async function probe(file: string): Promise<SourceInfo> {
  const { stdout } = await run('ffprobe', ['-v', 'error', '-select_streams', 'a:0', '-show_entries', 'stream=codec_name,sample_rate,bits_per_raw_sample,bits_per_sample:format=duration', '-of', 'json', file])
  return parseProbe(stdout)
}

export async function ffmpeg(args: string[]): Promise<void> {
  await run('ffmpeg', args, { maxBuffer: MAX_BUFFER })
}

export async function measureLoudness(file: string): Promise<number> {
  const { stderr } = await run('ffmpeg', ['-hide_banner', '-nostats', '-i', file, '-af', 'ebur128=framelog=quiet', '-f', 'null', '-'], { maxBuffer: MAX_BUFFER })
  return parseLoudness(stderr)
}

/** Mono 16 kHz float samples, the input the fingerprinter expects. */
export async function decodeForFingerprint(file: string): Promise<Float32Array> {
  const { stdout } = await run('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-i', file, '-ac', '1', '-ar', '16000', '-f', 'f32le', '-'], { encoding: 'buffer', maxBuffer: MAX_BUFFER })
  return new Float32Array(stdout.buffer, stdout.byteOffset, stdout.byteLength / 4)
}
