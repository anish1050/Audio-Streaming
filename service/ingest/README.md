# Ingest

Run on your own PC, never on Cloud Run. Needs `ffmpeg` and `ffprobe` 7+ on PATH
(Windows: `winget install Gyan.FFmpeg`) and a filled `service/.env`.

## 1. Prepare an album folder

```
drops/
  arjun-rao/
    neon-city/
      album.json
      cover.jpg          square, at least 1000 px
      artist.jpg         optional
      01-midnight-drive.flac
      02-neon-rain.flac
```

`album.json`:

```json
{
  "rights_holder_id": "rh-arjun",
  "artist": { "id": "arjun-rao", "name": "Arjun Rao", "bio": "Night-drive lo-fi from Pune" },
  "album": { "id": "neon-city", "title": "Neon City", "year": 2026, "genres": ["lo-fi"] },
  "tracks": [
    { "file": "01-midnight-drive.flac", "title": "Midnight Drive" },
    { "file": "02-neon-rain.flac", "title": "Neon Rain", "free_full_length": true },
    { "file": "03-cc-track.flac", "title": "Open Sky", "licence": "CC-BY-4.0", "attribution": "Open Sky by Lune, CC BY 4.0" }
  ]
}
```

Ids are lowercase letters, digits and dashes. Masters must be FLAC (16-bit/44.1 kHz or better)
or DSD; MP3/AAC are rejected. A signed agreement must exist in `rights_holders` before ingest.

## 2. Run

```bash
npm run db:indexes
npm run ingest:album -- --dir ../drops/arjun-rao/neon-city
```

Per track this makes `hires` (original, capped at 24/192), `lossless` (16/44.1), `high` and `low`
(Opus) and a 30-second `preview`, measures loudness, fingerprints for recognition and uploads to R2.

## 3. Recommendations (WSL2 or Docker)

```bash
python ingest/embed.py --work ../drops/.work
npm run ingest:neighbors
```

Then create the Atlas Vector Search index `track_embedding` on `catalog_tracks` once:

```json
{ "fields": [{ "type": "vector", "path": "embedding", "numDimensions": 512, "similarity": "cosine" }] }
```

## Other commands

| Command | Does |
|---|---|
| `npm run ingest:featured -- --tracks a,b,c` | Sets the Featured playlist on Home |
| `npm run ingest:remove -- --track <id>` | Takedown: deletes audio, catalog entry, fingerprints |
| `npm run royalties -- --month 2026-11 --net-revenue-paise <n>` | Monthly payout CSV per rights holder |
