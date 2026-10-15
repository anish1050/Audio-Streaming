import { describe, expect, it } from 'vitest'
import { isAcceptedSource, parseLoudness, parseProbe, tierArgs, tierFormat } from '../ingest/ffmpeg.js'

const dxd = { codec: 'flac', sampleRate: 352800, bitDepth: 24, durationMs: 240000 }
const cd = { codec: 'flac', sampleRate: 44100, bitDepth: 16, durationMs: 240000 }

describe('tierArgs', () => {
  it('caps hi-res at 192 kHz with soxr and keeps 24 bits', () => {
    const args = tierArgs('hires', dxd, 'in.flac', 'out.flac')!.join(' ')
    expect(args).toContain('aresample=192000:resampler=soxr')
    expect(args).toContain('-bits_per_raw_sample 24')
    expect(tierFormat('hires', dxd)).toEqual({ sampleRate: 192000, bitDepth: 24 })
  })

  it('skips hi-res for CD-quality masters', () => {
    expect(tierArgs('hires', cd, 'in.flac', 'out.flac')).toBeNull()
  })

  it('keeps hi-res for 24-bit masters at 44.1 kHz', () => {
    expect(tierArgs('hires', { ...cd, bitDepth: 24 }, 'in.flac', 'out.flac')).not.toBeNull()
  })

  it('dithers lossless down to 16/44.1', () => {
    const args = tierArgs('lossless', dxd, 'in.flac', 'out.flac')!.join(' ')
    expect(args).toContain('aresample=44100:resampler=soxr:dither_method=triangular')
    expect(args).toContain('-sample_fmt s16')
  })

  it('cuts a 30 second preview starting a quarter of the way in', () => {
    expect(tierArgs('preview', cd, 'in.flac', 'out.opus')).toEqual(expect.arrayContaining(['-ss', '60', '-t', '30', '-b:a', '96k']))
  })
})

describe('source checks', () => {
  it('parses ffprobe output', () => {
    const json = JSON.stringify({ streams: [{ codec_name: 'flac', sample_rate: '96000', bits_per_raw_sample: '24' }], format: { duration: '241.5' } })
    expect(parseProbe(json)).toEqual({ codec: 'flac', sampleRate: 96000, bitDepth: 24, durationMs: 241500 })
  })

  it('rejects lossy sources and accepts DSD', () => {
    expect(isAcceptedSource({ ...cd, codec: 'mp3' })).toBe(false)
    expect(isAcceptedSource({ codec: 'dsd_lsbf_planar', sampleRate: 352800, bitDepth: 24, durationMs: 1 })).toBe(true)
    expect(isAcceptedSource(cd)).toBe(true)
  })

  it('reads the integrated loudness from the ebur128 summary', () => {
    const stderr = 'frame...\n  Integrated loudness:\n    I:         -12.4 LUFS\n    Threshold: -22.6 LUFS\n'
    expect(parseLoudness(stderr)).toBe(-12.4)
  })
})
