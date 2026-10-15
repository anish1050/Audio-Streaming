# Hi-Res Music Streaming App - Design Spec

Date: 2026-10-15
Status: draft for review. Written without a Q&A round; every assumption is listed in section 18.

## 1. Goal

A subscription music streaming app in the Apple Music / Tidal / Qobuz class:
hi-res lossless playback at the highest quality Android can deliver, encrypted
offline downloads inside the app's private storage, song recognition,
Spotify-style recommendations, listening statistics, and the three-screen dark
UI from the attached mockup (Sound Intelligence, Featured Sounds, Music Library
with "AI Discovery").

Android first (Kotlin + Jetpack Compose). iOS later in Swift. The backend and
wire format are platform-neutral JSON so the iOS port reuses all of it.

**Zero initial investment.** Nothing is paid before revenue exists, except the
one-time USD 25 Google Play developer fee. Rights holders are paid monthly out
of subscription revenue that has already arrived.

## 2. What "zero initial investment" means for the catalog

This is the single most important constraint. The plan is only honest if it is stated plainly.

| Fact | Consequence |
|---|---|
| The three majors (Universal, Sony, Warner) and Merlin (the indie-label rights agency) license new services against a **minimum guarantee**, an advance paid before launch, plus a revenue share. There is no public precedent for a zero-advance major-label deal. | Apple-Music-scale catalog is impossible with zero investment. It becomes phase 2, negotiated with subscriber numbers and revenue in hand. |
| B2B catalog providers (7digital, owned by Songtradr; Tuned Global) bundle label licensing and delivery. Their public precedent (Triller, 2020) was a monthly platform fee plus usage fees over an 18-month minimum term. | Also not zero-cost. Send both a quote request in week 1 (template in section 17) so the numbers are ready when revenue is. |
| A catalog with no upfront cost exists only from: (a) artists who sign a direct non-exclusive licence and control 100 % of their master and publishing, paid by revenue share; (b) CC BY recordings (commercial use allowed; CC BY-NC is not); (c) public-domain recordings. Jamendo's free API is non-commercial only. | **Phase 1 catalog = direct indie artists on revenue share + CC BY + public domain.** Many independent artists already own hi-res masters. The full product (player, downloads, billing, recognition, recommendations) is built and proven on this catalog, so a label deal later plugs into a working app. |
| Publishing rights are separate from masters. India: IPRS (publishing) and PPL (sound recordings). USA: a new interactive service needs The MLC's blanket mechanical licence. | **Launch market = India.** The artist agreement warrants 100 % control of publishing or no IPRS registration. A US launch is a phase 2 legal task. |
| Industry norm: about **65–70 % of net revenue** goes to rights holders. Google Play keeps **15 %** of subscription revenue. India prices include 18 % GST. | Per ₹119 subscriber: ₹119 − GST ₹18.2 = ₹100.8 → Play 15 % = ₹15.1 → ₹85.7 net → rights 70 % ≈ ₹60 → you ≈ ₹26 before infrastructure. Infrastructure stays free until well past 1,000 subscribers (section 4). |

## 3. Competitor subscription prices (October 2026)

Monthly prices from the sources at the end. Several moved during 2026 and sources disagree by up to a dollar; confirm on each checkout page before quoting publicly.

### United States (USD per month)

| Service | Individual | Family | Student | Best quality |
|---|---|---|---|---|
| Spotify Premium | 12.99 | 21.99 (Duo 18.99) | 6.99 | Lossless up to 24/44.1, no hi-res |
| Apple Music | 11.99 | 19.99 | 6.99 | Hi-Res Lossless ALAC 24/192 (wired only) |
| Tidal | 11.99 | 19.99 | 6.99 | FLAC 24/192 + Dolby Atmos (MQA removed 2024) |
| Amazon Music Unlimited | 10.99 (9.99 with Prime); a 2025 report says 11.99 / 10.99 | 16.99–19.99 | 5.99 | Ultra HD FLAC 24/192 |
| Qobuz Studio | 12.99 (10.83 billed annually) | 17.99 | – | FLAC 24/192 |
| Deezer | 11.99 | 19.99 | 5.99 | FLAC 16/44.1 |
| YouTube Music Premium | 10.99 | 16.99 | 5.49 | Lossy AAC/Opus |

### India (INR per month)

| Service | Individual | Family | Student | Best quality |
|---|---|---|---|---|
| Spotify Premium | 139 | 229 (Duo 179) | 69 | Lossless where available |
| Apple Music | 139 (raised July 2026 from 119) | 229 | 69 | Hi-Res Lossless 24/192 |
| Amazon Music Unlimited (launched June 2026) | 119 (99 with Prime) | – | – | HD / Ultra HD |
| YouTube Music Premium | ≈ 119 (sources say 109–119) | ≈ 149 | ≈ 89 | Lossy |
| JioSaavn Pro | 99 | – | 49 | 320 kbps |
| Tidal, Qobuz | not available in India | | | |

### Pricing for this app (decided: competitive with the market, 1-week free trial)

| Plan | India | US (phase 2) | Notes |
|---|---|---|---|
| Free trial | 7 days free | 7 days free | New users only, once per Google account (Play enforces it). Full Premium during the trial. |
| Premium monthly | ₹119 | $10.99 | Same as Amazon Music Unlimited and YouTube Music, ₹20 under Spotify and Apple Music. |
| Premium annual | ₹1,199 (≈ ₹100/month, save 16 %) | $109.99 | |
| Family (phase 2, needs label catalog) | ₹199 | $16.99 | Up to 6 members. |
| Student (phase 2) | ₹59 | $5.99 | |

Every competitor in India sits between ₹99 and ₹139, so ₹119 is in the middle of the market, not a discount play. Hi-res, downloads, recognition and stats are the reason to switch, not price.

Free tier: 30-second previews of every track, full-length play of tracks the artist marks free, no downloads, no ads.

## 4. Zero-investment infrastructure

| Item | Phase 1 cost | Free limit | When it starts costing |
|---|---|---|---|
| Google Play developer account | **USD 25 once** | – | – |
| Google Cloud Run (API) | 0 | 2 M requests and 180k vCPU-seconds per month | Pay per use above that. A card on the billing account is required but not charged. |
| Google Pub/Sub (Play billing notifications) + Cloud Scheduler (nightly job) | 0 | 10 GB/month, 3 scheduler jobs | Far beyond phase 1. |
| MongoDB Atlas M0 (Mumbai, `ap-south-1`) | 0 | 512 MB, Vector Search included | Move to the usage-based Flex tier past ≈ 400 MB. |
| Cloudflare R2 (audio, covers) | 0 | 10 GB storage, **egress always free** | USD 0.015 per GB-month. 10 GB ≈ 120 hi-res tracks with all tiers; 1 TB (≈ 10,000 tracks) ≈ USD 15/month, paid from revenue. |
| GitHub + Actions | 0 | 2,000 CI minutes/month on private repos | – |
| Media3, Compose, ffmpeg, LAION-CLAP, Manrope font | 0 | open source | – |
| Recognition outside our catalog (AudD) | 0, **switched off** | 300 free requests | USD 2–5 per 1,000. Switch on from revenue with a monthly cap. |
| Login | 0 | Google Sign-In | Phone OTP is not used because every SMS costs money. |
| Privacy policy, deletion page | 0 | GitHub Pages | – |
| Artist agreement legal review | 0 if skipped | – | **Recommended** before signing the first outside artist. This is the only "investment" worth reconsidering. |

**Why not Vercel:** your other services run on Vercel, but the Hobby plan is restricted to non-commercial personal use. A paid subscription app breaks those terms. Cloud Run's free tier allows commercial use. Pub/Sub and the Play Developer API service account need a Google Cloud project anyway, so everything lives in one project. The code stays the same Hono app. If you later move to Vercel Pro (USD 20/month) after revenue, only the entry file changes.

## 5. Non-negotiable constraints

| Constraint | Value |
|---|---|
| Client | Android only in phase 1. Kotlin 2.1, Jetpack Compose, Material 3. minSdk 27 (the platform FLAC decoder is mandatory from API 27), targetSdk 35. |
| Playback | AndroidX Media3 (ExoPlayer + MediaSession) 1.8.0 or newer. Float audio output on. Platform `MediaCodec` FLAC decoder. |
| Offline | Media3 `DownloadManager` writing into app-private storage through AES-encrypted cache spans. Never on shared storage. Playable only while the entitlement holds, with a 30-day offline grace. |
| Backend | Hono 4 + TypeScript on Node 24, MongoDB native driver, run by `@hono/node-server` in a Cloud Run container. Same conventions as `foodxp-b2c-service` and the Expense Tracker service: `#helpers/*` imports, `{ success, data }` envelope, Zod `.strict()` on every body, snake_case wire and DB keys, `MongoClient*` helpers only, no `as any`, `Date` in UTC. |
| Database | Its own Atlas project and database `hires`. Never shared with travelxp or foodxp. |
| Audio storage | Cloudflare R2 via its S3 API. Presigned GET URLs: 15 min for streams, 6 h for downloads. The bucket is never public. |
| Ingest | Local CLI in `service/ingest/` run on your PC (tsx + ffmpeg). Embeddings by `embed.py` (Python, LAION-CLAP) under WSL2 or Docker. Cloud Run never runs ffmpeg. |
| Recommendations | LAION-CLAP 512-d audio embeddings in Atlas Vector Search, plus item-to-item co-play neighbours from a nightly job. Matrix factorisation only after 10k users. |
| Recognition | In-house landmark fingerprinting over our own catalog. AudD fallback built but off. |
| Billing | Google Play Billing Library 8.0.0 or newer. One subscription product `premium`, base plans `monthly` and `annual`, offer `trial7`. Server-side verification and acknowledgement through the Play Developer API. Real-time Developer Notifications (RTDN) via Pub/Sub push. |
| Repo | Monorepo at https://github.com/anish1050/Audio-Streaming: `android/`, `service/`, `shared/`, `docs/`. Branch flow `feature → master → staging → production`. One commit per task. |
| Brand | Unnamed. The Android `applicationId` and package are brand-neutral `com.anish1050.hires`, because Play makes the applicationId permanent. The brand appears only in `app_name`, `package.json` `name` and docs. |
| Money | `Long` minor units (paise) everywhere. Never `Double`. |
| Privacy | The recognition clip is recorded only on tap, is never stored, and is discarded after matching. Play events carry no audio. |

## 6. Architecture

```
+------------------------------ Android (Kotlin/Compose) ------------------------------+
| UI: Home(Featured) | Stats | Discover tabs, Library, Now Playing sheet, Search,      |
|     Recognize, Paywall, Settings, Onboarding                                         |
|   |                                                                                  |
| ViewModels -> Repositories -> HiResApi (OkHttp) -------------------------+            |
|   |                      \-> Room: downloaded_tracks, pending_plays        |            |
| PlaybackService (Media3 MediaSessionService)                              |            |
|   ExoPlayer, float output, FLAC via MediaCodec                            |            |
|   ResolvingDataSource: app://stream/<id> -> presigned URL                 |            |
|   CacheDataSource (read-only) over encrypted download cache               |            |
|   QualityPolicy (pure)   SignalPath (what really reaches the DAC)         |            |
|   UsbMixer (API 34: USB DAC at the track's sample rate)                   |            |
| DownloadService (Media3) -> same encrypted cache, write enabled           |            |
| PlayBilling (BillingClient)     MicRecorder (16 kHz PCM, 8 s)             |            |
+---------------------------------------------------------------------------|------------+
                                                                            | HTTPS + JWT
+---------------------------------------------------------------------------v------------+
| service: Hono on Cloud Run (asia-south1)                                                |
|  POST /api/auth/google          GET|DELETE /api/me                                      |
|  GET  /api/home /api/search /api/tracks/:id /api/albums/:id /api/artists/:id            |
|  GET  /api/tracks/:id/stream?quality=   GET /api/tracks/:id/download?quality=           |
|  POST /api/plays   GET /api/stats   GET|POST|DELETE /api/library                        |
|  GET  /api/discover              POST /api/recognize                                    |
|  POST /api/billing/google/verify POST /api/billing/google/rtdn (Pub/Sub push)           |
|  POST /api/jobs/coplay (Cloud Scheduler, nightly)       GET /api/health                 |
+------------------+-----------------------------------+------------------------------------+
                   |                                   |
     +-------------v------------+         +------------v-----------+
     | MongoDB Atlas M0         |         | Cloudflare R2          |
     | catalog, users, plays,   |         | audio tiers, previews, |
     | subscriptions, vectors,  |         | covers                 |
     | fingerprints             |         +------------------------+
     +-------------^------------+                      ^
                   |                                   |
     +-------------+-----------------------------------+---+
     | service/ingest (local CLI on your PC)                |
     | ffprobe/ffmpeg tiers, EBU R128 loudness, fingerprint,|
     | R2 upload, Mongo writes, embed.py (CLAP), neighbours,|
     | monthly royalty report                               |
     +------------------------------------------------------+
```

## 7. Android app

### 7.1 Stack (copied from the Expense Tracker unless noted)

- Kotlin 2.1.20, Compose BOM 2025.04+, Material 3, Navigation Compose 2.8. Version catalog `gradle/libs.versions.toml`, Kotlin DSL.
- **New for this app:** Media3 1.8.0+ (`media3-exoplayer`, `media3-session`, `media3-datasource-okhttp`), Coil 2.7 for cover art, Play Billing `billing-ktx` 8.0.0+.
- Room 2.7 (KSP), OkHttp 4.12, kotlinx.serialization, Credential Manager + googleid, `security-crypto` (`EncryptedSharedPreferences`).
- Manual DI: one `AppContainer` with `by lazy` fields, reached via `Context.container`. No Hilt.
- MVVM: ViewModels take `AppContainer`, expose `StateFlow<XState>` via `stateIn(WhileSubscribed(5000))`. State classes are `@Immutable` data classes. `Application.onCreate` stays empty except StrictMode in debug.

### 7.2 Package layout (single module `app`, package `com.anish1050.hires`)

```
app/src/main/java/com/anish1050/hires/
  HiResApp.kt  AppContainer.kt  MainActivity.kt
  data/
    api/        HiResApi.kt, Dto.kt, AuthStore.kt
    db/         HiResDatabase.kt, Entities.kt, Daos.kt
    repo/       CatalogRepository, LibraryRepository, StatsRepository,
                EntitlementRepository, PlayEventRepository
  playback/     PlaybackService, PlayerFactory, QualityPolicy, SignalPath, UsbMixer,
                PlayTracker, TrackUris
  download/     DownloadStore, HiResDownloadService, DownloadsRepository
  recognize/    MicRecorder
  billing/      PlayBilling
  ui/
    Nav.kt  Components.kt
    theme/      Color.kt, Type.kt, Theme.kt
    home/ stats/ discover/ library/ player/ search/ album/ artist/
    downloads/ settings/ paywall/ recognize/ onboarding/
```

### 7.3 Screens (from the mockup)

| Screen | Mockup | Content |
|---|---|---|
| Home (Featured) | middle phone | Blurred full-bleed hero of today's Daily Mix, "DAILY MIX / ENDLESS VIBES" title, circular cover carousel (snap, centre item scaled 1.3×), tuner-style dial scrubber, previous / next, bottom row: search, play, save. Below: Recently played, Featured playlists. |
| Stats | left phone | "Sound Intelligence". Listening Time card: `18h 42m`, top-artist avatars with `+32`, songs played, peak time window. Genre Distribution card: coloured pill bars per genre on a grid, legend chips (Lo-fi, Pop, R&B, Others). Week / month toggle. |
| Library | right phone | "Music Library". Cards with cover, title, subtitle, artist chip, arrow. Sections: Downloads, Liked, Albums, Artists, Playlists. Rose-gradient "AI Discovery" card: `24 tracks`, `AI Match 96%`, arc sparkline; opens Discover. |
| Discover | – | AI Discovery list (24 tracks with match %), three Daily Mixes, "Recognize a song" button. |
| Now Playing | – | Expands from the mini player: cover, dial scrubber, controls, signal-path badge ("FLAC 24/96 → float → 96 kHz → USB DAC"), like, download, queue. |
| Search | header icon | Tracks, albums, artists; 300 ms debounce. |
| Recognize | – | Pulsing button, 8-second listen, result card with Play / Save, or "Not in our catalog yet". |
| Paywall | – | Monthly / annual, 7-day trial, restore purchases. |
| Settings | – | Streaming quality (Wi-Fi, mobile), download quality, hi-res over Bluetooth, loudness normalisation, USB DAC rate matching, account, delete account, licences. |
| Onboarding | – | Google sign-in, then pick 3+ genres and 3+ artists. |

Bottom bar: three tabs as in the mockup (Home, Stats, Discover). Library opens from the header icon on Home, as in the mockup's right phone.

Theme tokens: background `#0B0B0D`, card `#161618`, card border `#232326`, text `#F4F4F5`, muted `#8E8E93`, lavender `#AAB4FF`, sand `#E8C98A`, mint `#A8D8C4`, AI gradient `#3B2A2E → #C9897B`. Card radius 24 dp, tile radius 16 dp. Font: Manrope via downloadable Google Fonts.

### 7.4 Hi-res playback path

1. **Float output.** `PlayerFactory` builds ExoPlayer with float audio output enabled. Media3 then asks the FLAC `MediaCodec` for 32-bit float PCM. Without it, Media3 converts high-resolution PCM to 16-bit, which turns a 24-bit master into CD quality behind a hi-res badge. Float32 has a 24-bit mantissa, so 24-bit samples pass through exactly.
2. Audio attributes `USAGE_MEDIA` / `AUDIO_CONTENT_TYPE_MUSIC`. Media3 handles audio focus. Pause when headphones unplug.
3. **SignalPath** reads `AnalyticsListener.onAudioInputFormatChanged` (codec, sample rate, bit depth of the source) and `onAudioTrackInitialized` (encoding and sample rate handed to Android), plus the active output `AudioDeviceInfo`. The Now Playing badge shows exactly this chain and never more.
4. **UsbMixer (Android 14+).** When a USB DAC is the active output, it picks the DAC's supported mixer format whose sample rate equals the track's and whose bit depth is highest, and calls `setPreferredMixerAttributes`. Android then reopens the USB stream at the track's rate, so 96 kHz stays 96 kHz with no resampling. Volume is still applied by Android. True bit-perfect mode (`MIXER_BEHAVIOR_BIT_PERFECT`) needs an integer-PCM audio track matching the DAC format, which Media3's default sink cannot produce; a custom audio sink is a phase 2 task.
5. On the phone's built-in DAC, Android mixes at the device rate (usually 48 kHz). Bluetooth is lossy at any setting (LDAC tops out near 990 kbps). The badge says both honestly.
6. **Loudness normalisation.** Ingest stores EBU R128 integrated loudness per track. With the toggle on, the app sets `player.volume = 10^((−14 − lufs) / 20)`, capped at 1.0. Default off for hi-res listening.
7. **Gapless.** FLAC has no encoder padding, so consecutive FLAC items play gapless with Media3's defaults. No crossfade.
8. **Quality choice** is a pure function `QualityPolicy.choose(isPremium, prefs, network, output)`. Defaults: Wi-Fi → hires, mobile data → lossless, Bluetooth → lossless unless the user opts in. The server falls back to the best existing tier if the asked one is missing.

"Highest quality in the world" in practice: every major hi-res service tops out at **PCM 24-bit / 192 kHz FLAC or ALAC**. Above that only download stores sell DXD (24/352.8) and DSD. Android cannot play DSD natively. This app ingests DSD and DXD masters and serves them as FLAC 24/176.4 or 24/192, so it matches the best streaming services and never pretends to exceed what the phone outputs.

### 7.5 Track URIs, streaming and downloads

- A `MediaItem` never holds a presigned URL; those expire in 15 minutes while a queue can last hours. Instead:
  - Streaming items use `app://stream/<track_id>`. A `ResolvingDataSource` calls `GET /api/tracks/:id/stream?quality=` when ExoPlayer opens the item and swaps in the presigned R2 URL.
  - Downloaded items use `app://offline/<track_id>` with custom cache key `track:<track_id>`.
  - Download requests use `app://download/<track_id>?quality=` with the same custom cache key, resolved through `GET /api/tracks/:id/download`.
- **One encrypted cache**: `SimpleCache` in `filesDir/downloads`, `NoOpCacheEvictor`, `StandaloneDatabaseProvider`.
  - The download writer uses a `CacheDataSource.Factory` whose sink is `AesCipherDataSink(key, CacheDataSink(cache, …))`. `DownloadManager` is built from `DefaultDownloadIndex` + `DefaultDownloaderFactory(thatFactory, executor)`. The convenience constructor would write plaintext.
  - The player uses the same cache **read-only** (`setCacheWriteDataSinkFactory(null)`) with `AesCipherDataSource(key, FileDataSource())` for reads. Streaming items have no `track:` key, so they always miss the cache and never fill the phone's storage.
  - The key is 16 random bytes created on first launch and stored in `EncryptedSharedPreferences`.
  - `ponytail:` AES-CTR with a nonce derived from the cache key stops copying files off the phone. If a label deal later demands Widevine, downloads move to DASH + Widevine in phase 2.
- Room table `downloaded_tracks` holds metadata (title, artist, cover, quality, bytes) for the Downloads screen and the offline Library.
- **Offline gate**: `canPlayOffline(now) = now < min(entitlement.expires_at + 7 days, entitlement.verified_at + 30 days)`. When false, downloads are greyed out with "Go online to renew".
- `android:allowBackup="false"`; `dataExtractionRules` exclude `downloads/` and the databases.

### 7.6 Recognition client

`MicRecorder` records 8 s of 16 kHz mono 16-bit PCM with `AudioRecord` (`MediaRecorder.AudioSource.UNPROCESSED` when supported, else `MIC`). It posts the raw bytes (`application/octet-stream`, 256,000 bytes) to `POST /api/recognize`. The result card shows the track and the position ("you're at 1:12") with Play, or "Not in our catalog yet. Request it", which stores a `catalog_requests` row.

## 8. Backend

### 8.1 Layout

```
service/
  Dockerfile              node:24-slim, npm ci --omit=dev, node dist/server.js
  package.json            type module, imports map "#helpers/*" etc.
  tsconfig.json  vitest.config.ts
  src/server.ts           @hono/node-server on PORT (Cloud Run sets it; dev 3003)
  src/app.ts              routes, envelope, onError
  src/helpers/            env, mongo-client, response, jwt, google-id-token, r2, quality-gate,
                          entitlement, play-developer-api, fingerprint, recs, stats
  src/middleware/         auth, rate-limit, request-logger
  src/routes/             auth, me, catalog, audio, plays, library, stats, discover,
                          recognize, billing, jobs
  src/types/              catalog, user, billing
  ingest/                 ingest-album.ts, fingerprint-track.ts, embed.py, neighbors.ts,
                          royalty-report.ts, remove-track.ts, seed-editorial.ts
  tests/                  vitest + mongodb-memory-server
```

### 8.2 Collections (database `hires`, snake_case keys)

| Collection | Key fields | Indexes |
|---|---|---|
| `core_users` | `_id` uuid, `google_sub`, `email`, `name`, `picture`, `country`, `taste_genres[]`, `taste_artist_ids[]`, `created_at` | `google_sub` unique |
| `catalog_artists` | `_id`, `name`, `slug`, `bio`, `image_key`, `rights_holder_id`, `created_at` | `slug` unique; text `name` |
| `catalog_albums` | `_id`, `artist_id`, `title`, `year`, `cover_key`, `upc`, `genres[]`, `created_at` | `artist_id`; text `title` |
| `catalog_tracks` | `_id`, `fp_id` (int), `album_id`, `artist_id`, `title`, `track_no`, `duration_ms`, `isrc`, `explicit`, `genres[]`, `loudness_lufs`, `free_full_length`, `assets` {quality → `{key, codec, sample_rate, bit_depth, bitrate_kbps, bytes}`}, `embedding[512]`, `rights_holder_id`, `created_at` | `album_id`; `artist_id`; `fp_id` unique; text `title`; Atlas vector index `track_embedding` (512, cosine) |
| `catalog_playlists` | `_id`, `kind` (editorial), `title`, `cover_key`, `track_ids[]`, `updated_at` | `kind` |
| `rights_holders` | `_id`, `name`, `email`, `payout_share` (0.70), `agreement_signed_at`, `agreement_doc_key`, `payout_upi_masked` | – |
| `library_items` | `user_id`, `kind`, `item_id`, `created_at` | `{user_id, kind, item_id}` unique |
| `play_events` | `_id` = `client_event_id`, `user_id`, `track_id`, `artist_id`, `genres[]`, `ms_played`, `duration_ms`, `quality`, `offline`, `tier`, `started_at` | `{user_id, started_at}`; `{track_id, started_at}` |
| `subscriptions` | `_id` = user_id, `provider`, `purchase_token`, `product_id`, `base_plan_id`, `state`, `expires_at`, `auto_renewing`, `verified_at` | `purchase_token` unique |
| `billing_events` | `_id` = Pub/Sub message id, `purchase_token`, `notification_type`, `received_at`, `processed_at`, `error` | – |
| `fingerprints` | `h` (int), `t` (int frame), `tid` (int `fp_id`) | `{h: 1}` |
| `track_neighbors` | `_id` = track_id, `content[{track_id, score}]`, `coplay[{track_id, score}]`, `updated_at` | – |
| `catalog_requests` | `user_id`, `title`, `artist`, `created_at` | – |
| `counters` | `_id` = name, `seq` | – |

### 8.3 Endpoints

Every response is `{ success: true, data }` or `{ success: false, error: { code, message } }`. Auth is `Authorization: Bearer <app JWT>` (HS256, 30 days, `sub` = user id). Rate limits: auth 10/min per IP, recognize 10/min per user, plays 60/min per user, everything else 120/min per user. `ponytail:` rate limits are in-memory per Cloud Run instance; move to a Mongo TTL collection if abuse appears.

| Method and path | Auth | Purpose |
|---|---|---|
| `POST /api/auth/google` | – | Google ID token → app JWT + user |
| `GET /api/me` | user | user, `entitlement {tier, expires_at, verified_at}` |
| `PATCH /api/me/taste` | user | onboarding genres + artist ids |
| `DELETE /api/me` | user | delete user, library, plays, subscription row (Play policy) |
| `GET /api/home` | user | `featured[]`, `daily_mixes[3]`, `recent[]`, `discover_preview {count, match_percent, sparkline[16]}` |
| `GET /api/search?q=` | user | tracks, albums, artists (max 20 each) |
| `GET /api/tracks/:id`, `/api/albums/:id`, `/api/artists/:id` | user | catalog reads |
| `GET /api/tracks/:id/stream?quality=` | user | `{url, quality, codec, sample_rate, bit_depth, loudness_lufs, expires_at}` |
| `GET /api/tracks/:id/download?quality=` | premium | same shape, 6 h expiry |
| `POST /api/plays` | user | ≤ 200 events, idempotent on `client_event_id` |
| `GET /api/stats?period=7d\|30d` | user | `total_ms`, `songs_played`, `peak_hour`, `top_artists[]`, `genres[]` |
| `GET /api/library?kind=`, `POST /api/library`, `DELETE /api/library/:kind/:id` | user | saved items |
| `GET /api/discover` | user | `tracks[24]` with `match_percent` |
| `POST /api/recognize` | user | raw PCM → `{match: {track, offset_ms, confidence} \| null, external: {title, artist} \| null}` |
| `POST /api/billing/google/verify` | user | `{purchase_token}` → verified entitlement |
| `POST /api/billing/google/rtdn?token=` | shared secret | Pub/Sub push; re-reads state from Play |
| `POST /api/jobs/coplay` | `Authorization: Bearer CRON_SECRET` | nightly co-play neighbours |
| `GET /api/health` | – | `{ok: true}` |

### 8.4 Entitlement (pure, tested)

`tier = 'premium'` when the subscription state is `SUBSCRIPTION_STATE_ACTIVE`, `SUBSCRIPTION_STATE_IN_GRACE_PERIOD` or `SUBSCRIPTION_STATE_CANCELED` (cancelled but paid up) **and** `expires_at > now`. Everything else is `free`. Entitlement is never put in the JWT; the app reads it from `GET /api/me`.

### 8.5 Quality gate (pure, tested)

`allowedQuality(requested, tier, track)`:
- `free`: `low` if `track.free_full_length` and that asset exists, else `preview`.
- `premium`: the requested tier if it exists, else the next lower one in `hires > lossless > high > low`.

## 9. Catalog ingest (local CLI)

`npx tsx ingest/ingest-album.ts --dir ./drops/<artist>/<album> --artist <id> --rights <id>`

An album folder holds `album.json`, `cover.jpg` and `NN-title.flac` (or `.dsf`). Per track:
1. `ffprobe`: must be FLAC (or DSD, converted to FLAC 24/176.4 first), at least 16-bit / 44.1 kHz. MP3 and AAC sources are rejected; lossy audio is never upsampled and called hi-res.
2. Tiers with ffmpeg:
   - `hires` = original FLAC; anything above 192 kHz is resampled to 192 kHz with the soxr resampler.
   - `lossless` = FLAC 16/44.1 with triangular dither.
   - `high` = Opus 160 kbps; `low` = Opus 64 kbps; `preview` = Opus 96 kbps, 30 s starting at 25 % of the track.
   - `hires` is skipped when the source is already 16/44.1 (`lossless` covers it).
3. Loudness: ffmpeg `ebur128` filter → integrated LUFS.
4. Fingerprint: decode to 16 kHz mono float, `landmarks()`, insert into `fingerprints`.
5. Upload tiers and cover to R2: `audio/<track_id>/<quality>.<ext>`, `covers/<album_id>.jpg`.
6. Upsert `catalog_artists`, `catalog_albums`, `catalog_tracks`.

Then `python ingest/embed.py` (LAION-CLAP, model `music_audioset_epoch_15_esc_90.14.pt`) writes `embedding` and zero-shot `genres` for tracks missing them, and `npx tsx ingest/neighbors.ts` recomputes content neighbours by brute-force cosine (fine to 10k tracks).

`npx tsx ingest/royalty-report.ts --month 2026-11 --net-revenue-paise <n>` writes one CSV row per rights holder: `share = holder_premium_ms / total_premium_ms`, `payout_paise = floor(net_revenue × payout_share × share)`. Net revenue comes from the Play Console earnings report. You pay out by UPI or bank transfer from revenue already received.

## 10. Recommendations

- **Taste vector** = weighted mean of the embeddings of the user's last 200 plays (weight = `ms_played / duration_ms`, liked tracks ×2). Cold start = mean of the embeddings of the onboarding artists' tracks.
- **Candidates** = top 100 from Atlas Vector Search, plus co-play neighbours of the last 20 played tracks, plus editorial featured.
- **Score** = `0.6 · cosine + 0.3 · coplay + 0.1 · freshness − 0.5 · heard_in_last_14_days`.
- `match_percent = round(50 + 50 · cosine)`, clamped to 50–99.
- **AI Discovery** = top 24. **Daily Mixes** = candidates grouped by first genre; top 3 genres; 30 tracks each; shuffled with seed `user_id + date`.
- **Nightly co-play job**: for each pair of tracks played ≥ 30 s by the same user in the last 30 days, `score = co_users / sqrt(users_a · users_b)`; keep the top 50 per track.

## 11. Recognition (server)

Landmark hashing (Wang 2003), dependency-free, in `helpers/fingerprint.ts`:
- 16 kHz mono float input. STFT: 1024-point Hann window, hop 512 (32 ms per frame), own radix-2 FFT.
- Peaks: spectrogram points that are the maximum of a ±10-frame × ±20-bin neighbourhood and at least 10 dB above the clip's mean level. This gives roughly 5–15 peaks per second.
- Pairs: each peak with the next 5 peaks 1–63 frames later. `hash = f1 << 16 | f2 << 6 | dt`, stored with the anchor frame.
- Match: fetch stored rows for the query hashes, vote on `(track, t_stored − t_query)`, the best bin wins if it has ≥ 8 votes and ≥ 3 % of the query's hashes. `offset_ms = offset_frames × 32`.
- Size: about 5,000 rows per 4-minute track, so 120 tracks ≈ 600k rows ≈ 50 MB in M0. `ponytail:` fits phase 1; past ~2,000 tracks, move fingerprints to a packed per-track binary index.
- No match and `AUDD_ENABLED=true` with this month's count under `AUDD_MONTHLY_CAP` → forward the clip to AudD and return `external`.

## 12. Billing

Reuses the pattern from foodxp and travelxp: verify with the store, write the subscription in one place, save every webhook before processing it.

- **Play Console**: subscription `premium`, base plans `monthly` (₹119) and `annual` (₹1,199), offer `trial7` (7 days free, new customers only).
- **App**: connect → `queryProductDetails("premium")` → `launchBillingFlow` with `setObfuscatedAccountId(user_id)` → on purchase, `POST /api/billing/google/verify` → refresh `GET /api/me`. Restore = `queryPurchasesAsync` + verify. The app never acknowledges; the server does.
- **Verify**: service-account JWT (signed with `jose`) → OAuth token → `GET purchases/subscriptionsv2/tokens/{token}`. Reject if `externalAccountIdentifiers.obfuscatedExternalAccountId` is not the caller's user id. This stops a token being replayed onto another account. Upsert `subscriptions`, acknowledge if pending, return the entitlement.
- **RTDN**: Pub/Sub push with `?token=RTDN_SECRET`. Insert into `billing_events` with `_id` = message id first; a duplicate means already handled, so return 200. Then re-read the token state from Play (the notification is only a trigger, never the truth) and upsert. Errors go into `billing_events.error` and return 500, so Pub/Sub retries.
- Not in phase 1: India User Choice Billing, Razorpay web checkout, family and student plans.

## 13. Stats (Sound Intelligence)

One aggregation over `play_events` for the period: total `ms_played`, distinct tracks, `ms` per hour of day in IST (peak = best hour; the app shows the 3-hour window around it, e.g. "9 PM–12 AM"), top 10 artists by `ms` with image URLs, `ms` by first genre (top 3 + Others). The app draws the genre grid with Compose `Canvas`, no chart library.

## 14. Security and compliance

| Area | Rule |
|---|---|
| Secrets on device | JWT, device id, download key in `EncryptedSharedPreferences`. Never logged. |
| Downloads | App-private, AES-encrypted spans, `allowBackup=false`, excluded from data extraction. |
| Streams | Presigned URLs only (15 min). Download URLs only for premium. |
| Server input | Zod `.strict()` everywhere. Recognize body exactly 256,000 bytes max 320,000. Plays ≤ 200 rows, `ms_played ≤ duration_ms + 5000`, `started_at` within the last 30 days. |
| Server secrets | Cloud Run env from Secret Manager (free for 6 active secrets): `MONGODB_URI`, `JWT_SECRET`, `R2_*`, `PLAY_SERVICE_ACCOUNT_JSON`, `RTDN_SECRET`, `CRON_SECRET`, `AUDD_API_TOKEN`. Never in `vercel.json`-style files or the repo. |
| Billing | No entitlement without a verified token. RTDN rejected without the secret and idempotent on message id. |
| Rights | Every track has a `rights_holder_id` with a signed agreement on file before ingest. Takedowns through a `legal@` address, removed within 48 h with `ingest/remove-track.ts`. |
| Artist agreement | Non-exclusive, worldwide, streaming + in-app offline, 70 % of net revenue attributable by premium listening time, 12-month term auto-renewing, 30-day termination, warranty of 100 % master + publishing control. |
| Play policy | Data Safety form, account deletion inside the app and on a web page, microphone rationale, foreground service types `mediaPlayback` and `dataSync`. |
| Logging | Android `Log.*` only in debug builds. Server logs `[module:op] reason`, never bodies or tokens. |

## 15. Testing

- **Service**: Vitest + mongodb-memory-server, files run one at a time. Table tests for the pure helpers (`entitlement`, `quality-gate`, `fingerprint`, `recs`, royalty maths). Route tests through `app.request()`. The fingerprint test builds a 20-second synthetic melody, fingerprints it, then matches a noisy 6-second excerpt and expects the right track and offset.
- **Android**: JVM tests (JUnit 4 + Truth) for `QualityPolicy`, the offline gate, `PlayTracker` and `SignalPath` text. One Robolectric test for the encrypted cache: bytes read back equal bytes written, and the file on disk differs from the plaintext.
- **On a real phone (phase 0 spike)**: float output confirmed in `onAudioTrackInitialized`; USB DAC rate switch confirmed on Android 14+.

## 16. Phases

| Phase | Weeks | Deliverable |
|---|---|---|
| 0 Bootstrap + spikes | 1 | Repo, CI, service and app skeletons, hi-res output proven on a phone. Quote requests sent. |
| 1 MVP (this plan) | 2–9 | Sections 7–13 on the indie/CC catalog. 12-tester closed test on Play for 14 days, then production. |
| 2 Catalog + revenue | after revenue | Label deal via 7digital / Tuned Global, DDEX ingest, Widevine if required, artist web portal, family/student plans, User Choice Billing, lyrics, bit-perfect audio sink, AudD on with a cap. |
| 3 Platforms | later | iOS (Swift, same API), Android Auto, Chromecast, Dolby Atmos, web player. |

## 17. Quote request (send in week 1)

To 7digital (Songtradr) and Tuned Global:

> We are launching a hi-res subscription music service in India on Android (₹119/month, offline downloads). Please quote: (1) catalog access to the three majors and Merlin for India; (2) revenue-share percentage, and whether a minimum guarantee or monthly platform fee applies below 10,000 subscribers; (3) delivery format (DDEX ERN, availability of 24-bit FLAC); (4) DRM requirements for offline playback; (5) term and termination; (6) royalty reporting format.

## 18. Assumptions made without asking

1. India first, INR first.
2. One Premium tier in phase 1; Family and Student wait for the label catalog.
3. No ad-supported tier.
4. Lyrics, podcasts, social features, multi-device control, Chromecast and Android Auto are out of phase 1.
5. Artist onboarding is a Google Form + signed PDF + the ingest CLI; a web portal is phase 2.
6. Python tooling runs under WSL2 or Docker on your Windows PC.
7. The LAION-CLAP weights' licence allows commercial use; confirm on the model card before the first ingest. If not, fall back to OpenL3 (MIT).

## Sources

Pricing: [RouteNote: Apple Music price 2026](https://routenote.com/radar/apple-music-price-how-much-does-it-cost-in-2026/), [NBC Palm Springs: Apple Music July 2026 increase](https://www.nbcpalmsprings.com/2026/07/19/apple-music-subscription-prices-increase-for-individual-family-and-student-plans), [SoundGuys: Spotify US price hike](https://www.soundguys.com/spotify-confirms-us-subscription-price-hike-151412/), [Consequence: Spotify US 2026](https://concerts.consequence.net/articles/spotify-price-hike-us-2026), [BGR: Apple Music 2026](https://www.bgr.com/2265143/how-much-is-apple-music-2026/), [FreeYourMusic: alternatives to Spotify](https://freeyourmusic.com/blog/alternatives-to-spotify), [FreeYourMusic: Tidal review](https://freeyourmusic.com/blog/tidal-music-review), [Subger: Qobuz](https://subger.com/en/service/qobuz), [Subger: Amazon Music Unlimited](https://subger.com/en/service/amazon-music-unlimited), [BOL News: Amazon Music price increase](https://www.bolnews.com/technology/amazon-increases-music-unlimited-subscription-prices), [Music Ally: Apple Music India increase](https://musically.com/2026/07/24/apple-music-increases-prices-in-india/), [Sportskeeda: Apple Music India prices compared](https://tech.sportskeeda.com/mobiles/apple-music-price-hike-india-new-subscription-prices-compared), [FoneArena: Spotify India price hike](https://www.fonearena.com/blog/460914/spotify-premium-price-hike-india-august-2025.html/amp), [About Amazon India: Music Unlimited plans](https://www.aboutamazon.in/news/entertainment/amazon-music-unlimited-india-price-plans), [Rolling Stone India: Amazon Music 2026](https://rollingstoneindia.com/amazon-music-unlimited-india-plans-price/), [TelecomTalk: JioSaavn Pro](https://telecomtalk.info/jiosaavn-pro-annual-plan-at-offer-price/1000910/), [Swadeshi Apps: India music apps 2026](https://swadeshiapps.com/blog/indian-alternatives-to-spotify), [PPC Land: YouTube Premium India](https://ppc.land/youtube-premium-india-prices/), [HiFiVision: Tidal in India](https://www.hifivision.com/threads/tidal-in-india.78891/), [Hifiverse: hi-res services compared 2026](https://www.hifiverse.io/articles/audio/2026-hi-res-streaming-services-compared), [TechRadar: Tidal drops MQA](https://www.techradar.com/audio/audio-streaming/tidals-waving-goodbye-to-mqa-and-sony-360-reality-audio-heres-what-you-need-to-know).

Licensing: [Music Week: 7digital powers Triller](https://www.musicweek.com/digital/read/7digital-to-power-music-streaming-for-triller/080745), [CelebrityAccess: Triller licenses majors via 7digital](https://celebrityaccess.com/2020/08/12/tiktok-rival-triller-licences-all-3-majors-merlin-via-7digital/), [Revelator: 7digital](https://revelator.com/data-integrations/7-digital), [Wikipedia: Merlin Network](https://en.wikipedia.org/wiki/Merlin_Network), [Music Business Research: master and publishing rights](https://musicbusinessresearch.wordpress.com/2024/09/02/the-music-streaming-economy-part-12-master-and-publishing-rights-in-music-streaming/), [Jamendo API terms](https://help-licensing.jamendo.com/hc/en-us/articles/20699346005661-Jamendo-API), [Wikipedia: Creative Commons](https://en.wikipedia.org/wiki/Creative_Commons), [Ones To Watch: streaming payouts 2026](https://resources.onestowatch.com/music-streaming-platforms-payouts-2026/).

Technology: [AOSP: preferred mixer attributes on USB](https://source.android.com/docs/core/audio/preferred-mixer-attr), [Media3 supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats), [Media3 ToFloatPcmAudioProcessor](https://developer.android.com/reference/kotlin/androidx/media3/exoplayer/audio/ToFloatPcmAudioProcessor), [ExoPlayer issue 6749: 16-bit truncation](https://github.com/google/ExoPlayer/issues/6749), [Google Play billing](https://developer.android.com/google/play/billing), [Foresight: Billing Library 8 migration](https://foresightmobile.com/blog/google-play-billing-library-8-migration), [Cleeng: Play billing subscriptions 2026](https://blog.cleeng.com/google-play-billing-subscriptions), [AudD](https://audd.io/), [ACRCloud docs](https://docs.acrcloud.com/tutorials/recognize-music), [arXiv 2608.06928: audio embeddings for recommendation](https://arxiv.org/pdf/2608.06928), [arXiv 2409.09026: CLAP embeddings for recommenders](https://arxiv.org/pdf/2409.09026).
