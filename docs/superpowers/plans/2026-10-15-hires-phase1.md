# Hi-Res Streaming Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship phase 1 of an Android hi-res subscription streaming app: FLAC up to 24/192 with float output, encrypted offline downloads, own-catalog song recognition, embedding + co-play recommendations, listening stats, Google Play subscriptions, and the three-screen mockup UI. Zero spend except the USD 25 Play fee.

**Architecture:** Kotlin + Compose app on Media3 talks to a Hono + MongoDB service on Cloud Run. Audio lives in Cloudflare R2 and is served through short-lived presigned URLs. A local ingest CLI transcodes, fingerprints and embeds every track.

**Tech Stack:** Kotlin 2.1, Compose BOM 2025.04, Media3 1.8, Room 2.7, Coil 2.7, Play Billing 8, Credential Manager, OkHttp 4.12, kotlinx.serialization. Node 24, Hono 4, TypeScript, mongodb 7 driver, jose 6, Zod 4, @aws-sdk/client-s3 + s3-request-presigner (R2), Vitest 4, mongodb-memory-server. ffmpeg 7, Python 3.11 + laion_clap.

**Spec:** `docs/superpowers/specs/2026-10-15-hires-streaming-design.md`

## Global Constraints

- Zero paid services. The only allowed spend is the USD 25 Play registration. Any task that would need money stops and asks.
- Android `applicationId` and package `com.anish1050.hires`. Brand only in `strings.xml` `app_name`.
- minSdk 27, targetSdk 35, compileSdk 35, JVM 17.
- Money is `Long` paise. Never `Double`.
- Wire and DB keys snake_case; Kotlin/TS variables camelCase; TS files kebab-case; Kotlin files PascalCase.
- Backend: `#helpers/*` imports, Zod `.strict()` on every body, `{ success, data }` envelope, `MongoClient*` helpers only, no `as any`, dates as UTC `Date`.
- Own database `hires`. Never point at travelxp or foodxp databases.
- Presigned URLs only; R2 bucket never public.
- No raw audio, tokens or request bodies in logs.
- Quality names, used identically on both sides: `preview`, `low`, `high`, `lossless`, `hires`.
- Dev env: `MONGODB_URI=mongodb://localhost:27017/`, `MONGODB_DBNAME=hires`, `PORT=3003`.
- Repo: https://github.com/anish1050/Audio-Streaming (monorepo). Commit after every task with a conventional message. Bootstrap on `master`; feature branches after the first release.

---

## Phase 0: Bootstrap and spikes

### Task 0.1: Monorepo skeleton and CI

**Files:**
- Create: `README.md`, `.gitignore`, `.github/workflows/ci.yml`, `shared/qualities.json`

**Produces:** `android/`, `service/`, `shared/`, `docs/` layout; `shared/qualities.json` read by both sides' tests.

- [ ] **Step 1: Git and remote** (already done on 2026-10-15; verify)

```bash
git init -b master
git remote add origin https://github.com/anish1050/Audio-Streaming.git
```

- [ ] **Step 2: Root files**

`.gitignore`:
```
node_modules/
dist/
.env
.env.local
*.log
drops/
android/.gradle/
android/**/build/
android/local.properties
android/*.jks
android/.idea/
service/ingest/.venv/
.DS_Store
```

`shared/qualities.json`:
```json
{ "order": ["hires", "lossless", "high", "low"], "preview": "preview" }
```

- [ ] **Step 3: CI**

`.github/workflows/ci.yml`: `dorny/paths-filter` decides jobs. `service`: Node 24, `npm ci`, `npm run typecheck`, `npm test` in `service/`. `android`: JDK 17 Temurin, `gradle/actions/setup-gradle` with Gradle 8.11.1, `gradle testDebugUnitTest assembleDebug` in `android/`. Triggers: push to `master`, `staging`, `production`; pull requests.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore: monorepo skeleton and CI"
```

### Task 0.2: Service skeleton on Cloud Run

**Files:**
- Create: `service/package.json`, `service/tsconfig.json`, `service/vitest.config.ts`, `service/Dockerfile`, `service/.env.example`, `service/src/server.ts`, `service/src/app.ts`, `service/src/helpers/env.ts`, `service/src/helpers/response.ts`, `service/src/helpers/mongo-client.ts`, `service/src/middleware/request-logger.ts`, `service/src/middleware/rate-limit.ts`, `service/tests/global-setup.ts`, `service/tests/health.test.ts`

**Produces:** `app` (Hono), `ok(c, data)`, `fail(c, status, code, message)`, `MongoClientFindOne/Find/InsertOne/InsertMany/UpdateOne/DeleteMany/Aggregate`, `getDb()`, `rateLimit({ windowMs, max, key })`.

- [ ] **Step 1: package.json**

```json
{
  "name": "hires-service",
  "private": true,
  "type": "module",
  "engines": { "node": ">=24" },
  "imports": {
    "#helpers/*": "./src/helpers/*.ts",
    "#middleware/*": "./src/middleware/*.ts",
    "#routes/*": "./src/routes/*.ts",
    "#types/*": "./src/types/*.ts"
  },
  "scripts": {
    "dev": "tsx --watch src/server.ts",
    "build": "esbuild src/server.ts --bundle --platform=node --format=esm --packages=external --outfile=dist/server.js",
    "start": "node dist/server.js",
    "typecheck": "tsc --noEmit",
    "test": "vitest run"
  },
  "dependencies": {
    "@aws-sdk/client-s3": "^3",
    "@aws-sdk/s3-request-presigner": "^3",
    "@hono/node-server": "^1",
    "@hono/zod-validator": "^0.5",
    "hono": "^4",
    "jose": "^6",
    "mongodb": "^7",
    "zod": "^4"
  },
  "devDependencies": {
    "@types/node": "^24",
    "esbuild": "^0.25",
    "mongodb-memory-server": "^10",
    "tsx": "^4",
    "typescript": "^5.9",
    "vitest": "^4"
  }
}
```

esbuild resolves the `#` imports map at build time, so `dist/server.js` has no `.ts` paths.

- [ ] **Step 2: Env and Mongo helpers**

`src/helpers/env.ts`:
```ts
function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`[env] missing ${name}`);
  return value;
}

export const env = {
  get mongoUri() { return required('MONGODB_URI'); },
  get mongoDbName() { return required('MONGODB_DBNAME'); },
  get jwtSecret() { return new TextEncoder().encode(required('JWT_SECRET')); },
  get googleClientId() { return required('GOOGLE_CLIENT_ID'); },
  get r2() {
    return {
      accountId: required('R2_ACCOUNT_ID'),
      accessKeyId: required('R2_ACCESS_KEY_ID'),
      secretAccessKey: required('R2_SECRET_ACCESS_KEY'),
      bucket: required('R2_BUCKET'),
    };
  },
  get playPackageName() { return required('PLAY_PACKAGE_NAME'); },
  get playServiceAccountJson() { return required('PLAY_SERVICE_ACCOUNT_JSON'); },
  get rtdnSecret() { return required('RTDN_SECRET'); },
  get cronSecret() { return required('CRON_SECRET'); },
};
```

Getters fail at first use, not at import, so tests only need the variables they touch.

`src/helpers/mongo-client.ts`: copy the shape of the Expense Tracker's `service/src/helpers/mongo-client.ts` (connection cached on `globalThis`, `maxPoolSize: 10`), exposing `getDb()` and typed wrappers `MongoClientFindOne<T>(collection, filter, options?)`, `MongoClientFind<T>`, `MongoClientInsertOne<T>`, `MongoClientInsertMany<T>(collection, docs, { ordered: false })`, `MongoClientUpdateOne<T>(collection, filter, update, { upsert? })`, `MongoClientDeleteMany<T>`, `MongoClientAggregate<T>(collection, pipeline)`. Each returns the driver result directly and throws on DB errors.

`src/helpers/response.ts`:
```ts
import type { Context } from 'hono';
import type { ContentfulStatusCode } from 'hono/utils/http-status';

export const ok = <T>(c: Context, data: T, status: ContentfulStatusCode = 200) =>
  c.json({ success: true, data }, status);

export const fail = (c: Context, status: ContentfulStatusCode, code: string, message: string) =>
  c.json({ success: false, error: { code, message } }, status);
```

- [ ] **Step 3: Rate limit (in-memory)**

`src/middleware/rate-limit.ts`:
```ts
import type { MiddlewareHandler } from 'hono';
import { fail } from '#helpers/response';

// ponytail: per-instance memory; move to a Mongo TTL collection if abuse appears.
export function rateLimit(opts: { windowMs: number; max: number; key: (c: Parameters<MiddlewareHandler>[0]) => string }): MiddlewareHandler {
  const hits = new Map<string, { count: number; resetAt: number }>();
  return async (c, next) => {
    const now = Date.now();
    const key = opts.key(c);
    const entry = hits.get(key);
    if (!entry || entry.resetAt <= now) {
      hits.set(key, { count: 1, resetAt: now + opts.windowMs });
    } else if (++entry.count > opts.max) {
      return fail(c, 429, 'rate_limited', 'Too many requests');
    }
    await next();
  };
}
```

- [ ] **Step 4: App and server**

`src/app.ts`:
```ts
import { Hono } from 'hono';
import { requestLogger } from '#middleware/request-logger';
import { ok, fail } from '#helpers/response';

export type AppEnv = { Variables: { userId: string } };

export const app = new Hono<AppEnv>();
app.use('*', requestLogger);
app.get('/api/health', c => ok(c, { ok: true }));
app.notFound(c => fail(c, 404, 'not_found', 'Not found'));
app.onError((err, c) => {
  console.error('[app:onError]', err.message);
  return fail(c, 500, 'internal', 'Something went wrong');
});
```

`src/server.ts`:
```ts
import { serve } from '@hono/node-server';
import { app } from './app.ts';

serve({ fetch: app.fetch, port: Number(process.env.PORT ?? 3003) });
```

`request-logger.ts` logs `method path status ms` only.

- [ ] **Step 5: Dockerfile**

```dockerfile
FROM node:24-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:24-slim
WORKDIR /app
ENV NODE_ENV=production
COPY package*.json ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
CMD ["node", "dist/server.js"]
```

- [ ] **Step 6: Test setup and health test**

`tests/global-setup.ts` starts `MongoMemoryServer`, sets `MONGODB_URI`, `MONGODB_DBNAME=hires_test`, `JWT_SECRET=test-secret-32-bytes-long-000000`, `GOOGLE_CLIENT_ID=test-client`, and stops it in teardown. `vitest.config.ts`: `globalSetup`, `fileParallelism: false`, `resolve.alias` for the four `#` prefixes.

`tests/health.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { app } from '../src/app.ts';

describe('health', () => {
  it('returns ok envelope', async () => {
    const res = await app.request('/api/health');
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ success: true, data: { ok: true } });
  });
});
```

- [ ] **Step 7: Run and commit**

Run: `cd service && npm install && npm run typecheck && npm test`
Expected: 1 passed.

```bash
git add service && git commit -m "feat(service): hono skeleton for cloud run"
```

### Task 0.3: Android skeleton with theme and tabs

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/xml/data_extraction_rules.xml`, `android/app/src/main/java/com/anish1050/hires/{HiResApp,AppContainer,MainActivity}.kt`, `.../ui/theme/{Color,Type,Theme}.kt`, `.../ui/Nav.kt`, `.../ui/Components.kt`

**Produces:** `HiResTheme {}`, `Routes` object, `GlassCard(modifier, content)`, `SectionHeader(title, subtitle)`, `Context.container`.

- [ ] **Step 1: Version catalog**

`libs.versions.toml` (versions are floors; take the latest patch):
```toml
[versions]
agp = "8.9.1"
kotlin = "2.1.20"
ksp = "2.1.20-2.0.0"
composeBom = "2025.04.00"
media3 = "1.8.0"
room = "2.7.0"
billing = "8.0.0"
coil = "2.7.0"
okhttp = "4.12.0"
serialization = "1.8.1"
navigation = "2.8.9"
lifecycle = "2.8.7"
credentials = "1.5.0"
googleid = "1.1.1"
securityCrypto = "1.1.0-alpha06"
junit = "4.13.2"
truth = "1.4.4"
robolectric = "4.14.1"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-text-google-fonts = { module = "androidx.compose.ui:ui-text-google-fonts" }
activity-compose = { module = "androidx.activity:activity-compose", version = "1.10.1" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-session = { module = "androidx.media3:media3-session", version.ref = "media3" }
media3-datasource-okhttp = { module = "androidx.media3:media3-datasource-okhttp", version.ref = "media3" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
billing-ktx = { module = "com.android.billingclient:billing-ktx", version.ref = "billing" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
credentials = { module = "androidx.credentials:credentials", version.ref = "credentials" }
credentials-play = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "credentials" }
googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "googleid" }
security-crypto = { module = "androidx.security:security-crypto", version.ref = "securityCrypto" }
junit = { module = "junit:junit", version.ref = "junit" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
media3-test-utils = { module = "androidx.media3:media3-test-utils", version.ref = "media3" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`app/build.gradle.kts`: `namespace` and `applicationId` `com.anish1050.hires`, minSdk 27, targetSdk 35, Compose on, `buildConfig = true`, `BuildConfig.API_BASE_URL` = `"http://10.0.2.2:3003"` for debug and the Cloud Run URL for release, `BuildConfig.GOOGLE_WEB_CLIENT_ID`. Release: `isMinifyEnabled = true`, `isShrinkResources = true`. Room schema export to `app/schemas`. `testOptions.unitTests.isIncludeAndroidResources = true`.

- [ ] **Step 2: Manifest**

Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `RECORD_AUDIO`, `WAKE_LOCK`. Application: `android:allowBackup="false"`, `android:dataExtractionRules="@xml/data_extraction_rules"`. Services are added in Tasks 2.2 and 3.2.

`data_extraction_rules.xml` excludes `file` path `downloads/` and `database` path `.` for both `cloud-backup` and `device-transfer`.

- [ ] **Step 3: Theme**

`ui/theme/Color.kt`:
```kotlin
package com.anish1050.hires.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0B0D)
val Card = Color(0xFF161618)
val CardBorder = Color(0xFF232326)
val TextPrimary = Color(0xFFF4F4F5)
val TextMuted = Color(0xFF8E8E93)
val Lavender = Color(0xFFAAB4FF)
val Sand = Color(0xFFE8C98A)
val Mint = Color(0xFFA8D8C4)
val Graphite = Color(0xFF3A3A3E)
val AiGradient = Brush.linearGradient(listOf(Color(0xFF3B2A2E), Color(0xFFC9897B)))
```

`Type.kt`: Manrope via `GoogleFont.Provider("com.google.android.gms.fonts", "com.google.android.gms", R.array.com_google_android_gms_fonts_certs)`; weights 400/500/700. Title style 28 sp bold with the second line in `TextMuted` (the mockup's "Sound / Intelligence" two-tone heading is built by `SectionHeader`).

`Theme.kt`: `darkColorScheme(background = Ink, surface = Card, primary = Lavender, onBackground = TextPrimary, onSurface = TextPrimary)`. The app is dark only.

- [ ] **Step 4: Nav and shell**

`ui/Nav.kt`: `object Routes { const val HOME = "home"; const val STATS = "stats"; const val DISCOVER = "discover"; const val LIBRARY = "library"; const val SEARCH = "search"; const val ALBUM = "album/{id}"; const val ARTIST = "artist/{id}"; const val DOWNLOADS = "downloads"; const val SETTINGS = "settings"; const val PAYWALL = "paywall"; const val RECOGNIZE = "recognize"; const val ONBOARDING = "onboarding" }`. Bottom bar: a floating pill (`Card` background, 28 dp radius) with three 48 dp icon buttons; the selected one gets a `Graphite` circle, as in the mockup.

`ui/Components.kt`: `GlassCard` = `Box` with `Card` background, 1 dp `CardBorder`, 24 dp radius, 20 dp padding. `SectionHeader(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit)`.

Each tab screen is a placeholder `Text` until its task.

- [ ] **Step 5: Build and commit**

Run: `cd android && gradle assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Install on a phone: dark shell with three tabs.

```bash
git add android && git commit -m "feat(android): compose shell, theme, tabs"
```

### Task 0.4: Hi-res output spike (throwaway, on a real phone)

**Files:**
- Create: `docs/spikes/hires-output.md` (results only; spike code is deleted)

- [ ] **Step 1: Prepare a test file**

Download a free 24/96 FLAC test track (for example from 2L's free "test bench" downloads) and push it to `/sdcard/Music/test-2496.flac` with `adb push`.

- [ ] **Step 2: Spike code in `MainActivity` (debug only)**

```kotlin
val player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableAudioFloatOutput(true)).build()
player.addAnalyticsListener(object : AnalyticsListener {
    override fun onAudioInputFormatChanged(e: AnalyticsListener.EventTime, f: Format, d: DecoderReuseEvaluation?) {
        Log.d("spike", "input ${f.sampleMimeType} ${f.sampleRate} Hz pcm=${f.pcmEncoding}")
    }
    override fun onAudioTrackInitialized(e: AnalyticsListener.EventTime, c: AudioSink.AudioTrackConfig) {
        Log.d("spike", "track encoding=${c.encoding} rate=${c.sampleRate}")
    }
})
player.setMediaItem(MediaItem.fromUri("file:///sdcard/Music/test-2496.flac"))
player.prepare(); player.play()
```

If `setEnableAudioFloatOutput` is deprecated in the pinned Media3 version, use the replacement named in its deprecation note.

- [ ] **Step 3: Record results**

Expected log: `track encoding=4 rate=96000` (4 = `C.ENCODING_PCM_FLOAT`). If encoding is 2 (16-bit), float output is not active: stop and fix before Phase 2. Repeat with a USB DAC on an Android 14+ phone and log `AudioManager.getSupportedMixerAttributes(usbDevice)`. Write the device, Android version, logged formats and DAC model into `docs/spikes/hires-output.md`.

- [ ] **Step 4: Remove spike code, commit notes**

```bash
git add docs/spikes && git commit -m "docs: hi-res output spike results"
```

### Task 0.5: Accounts and quote requests (no code)

- [ ] Create a Google Cloud project `hires-prod`. Enable Cloud Run, Artifact Registry, Cloud Build, Pub/Sub, Cloud Scheduler, Secret Manager and the Google Play Android Developer API. Add a billing account (needed for the free tier; set a **budget alert at ₹1**).
- [ ] Create a MongoDB Atlas M0 cluster in AWS Mumbai, database `hires`, user `hires-service`, network access `0.0.0.0/0` (Cloud Run has no fixed IP on the free tier).
- [ ] Create a Cloudflare R2 bucket `hires-audio` (location hint APAC), an API token scoped to that bucket, and a **₹0 billing notification**.
- [ ] Register the Google Play developer account (USD 25). Create the app `com.anish1050.hires`.
- [ ] Email the section 17 quote request from the spec to 7digital (Songtradr) and Tuned Global.
- [ ] Draft the artist agreement from spec section 14 and a one-page artist onboarding Google Form.

---

## Phase 1: Auth and catalog backend

### Task 1.1: Google sign-in, JWT, me, account deletion

**Files:**
- Create: `service/src/helpers/jwt.ts`, `service/src/helpers/google-id-token.ts`, `service/src/middleware/auth.ts`, `service/src/routes/auth.ts`, `service/src/routes/me.ts`, `service/src/types/user.ts`, `service/tests/auth.test.ts`, `service/tests/helpers.ts`
- Modify: `service/src/app.ts`

**Interfaces:**
- Produces: `signAppJwt(userId: string): Promise<string>`, `verifyAppJwt(token): Promise<string>` (returns user id), `authMiddleware` (sets `c.var.userId`), `newUserToken(): Promise<{ userId, token }>` in `tests/helpers.ts`, `CoreUserDoc`.

- [ ] **Step 1: Write the failing test**

`tests/auth.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { app } from '../src/app.ts';
import { newUserToken } from './helpers.ts';

describe('me', () => {
  it('rejects missing token', async () => {
    const res = await app.request('/api/me');
    expect(res.status).toBe(401);
  });

  it('returns user with free tier when no subscription', async () => {
    const { token, userId } = await newUserToken();
    const res = await app.request('/api/me', { headers: { authorization: `Bearer ${token}` } });
    const body = await res.json();
    expect(body.data.user.id).toBe(userId);
    expect(body.data.entitlement.tier).toBe('free');
  });

  it('deletes the account and its data', async () => {
    const { token } = await newUserToken();
    const del = await app.request('/api/me', { method: 'DELETE', headers: { authorization: `Bearer ${token}` } });
    expect(del.status).toBe(200);
    const after = await app.request('/api/me', { headers: { authorization: `Bearer ${token}` } });
    expect(after.status).toBe(401);
  });
});
```

`tests/helpers.ts` inserts a `core_users` doc with a random uuid and returns `{ userId, token: await signAppJwt(userId) }`.

- [ ] **Step 2: Run to see it fail**

Run: `npm test -- auth`
Expected: FAIL, 404 on `/api/me`.

- [ ] **Step 3: Implement**

`jwt.ts`: `new SignJWT({}).setProtectedHeader({ alg: 'HS256' }).setSubject(userId).setIssuedAt().setExpirationTime('30d').sign(env.jwtSecret)`; verify with `jwtVerify(token, env.jwtSecret, { algorithms: ['HS256'] })`.

`google-id-token.ts`: `createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'))`, `jwtVerify(idToken, jwks, { audience: env.googleClientId, issuer: ['https://accounts.google.com', 'accounts.google.com'] })`, return `{ sub, email, name, picture }`.

`middleware/auth.ts`: read Bearer token, verify, `MongoClientFindOne('core_users', { _id: userId }, { projection: { _id: 1 } })`; if missing → 401 `unauthorized`. Set `c.set('userId', userId)`.

`routes/auth.ts`: `POST /google` with `z.object({ id_token: z.string().min(20) }).strict()`, `rateLimit` 10/min per IP (`c.req.header('x-forwarded-for') ?? 'local'`), upsert user by `google_sub` (new users get `_id: crypto.randomUUID()`), return `{ token, user }`.

`routes/me.ts`: `GET /` returns `{ user, entitlement }` where entitlement comes from Task 4.1's `entitlementFor(userId, now)`. Until Task 4.1 lands, return `{ tier: 'free', expires_at: null, verified_at: null }` from a local function, and Task 4.1 replaces it. `DELETE /` runs `DeleteMany` on `library_items`, `play_events`, `subscriptions`, `core_users` for the user, and returns `{ deleted: true }`. Idempotent.

Mount in `app.ts`: `app.route('/api/auth', auth)`, `app.use('/api/me/*', authMiddleware)`, `app.use('/api/me', authMiddleware)`, `app.route('/api/me', me)`.

- [ ] **Step 4: Run tests, commit**

Run: `npm test -- auth`
Expected: 3 passed.

```bash
git add service && git commit -m "feat(service): google auth, me, account deletion"
```

### Task 1.2: Catalog reads and search

**Files:**
- Create: `service/src/types/catalog.ts`, `service/src/routes/catalog.ts`, `service/src/helpers/catalog-view.ts`, `service/tests/catalog.test.ts`, `service/tests/fixtures/catalog.ts`

**Interfaces:**
- Produces: `TrackDoc`, `AlbumDoc`, `ArtistDoc`, `Quality = 'preview' | 'low' | 'high' | 'lossless' | 'hires'`, `AssetInfo`, `trackView(track, album, artist): TrackView` (public shape: `id, title, duration_ms, explicit, album {id, title, cover_url}, artist {id, name}, best_quality {codec, sample_rate, bit_depth}`), `coverUrl(key)` (presigned 6 h, from Task 1.3; until then `/covers/<key>` placeholder string), `seedCatalog()` test fixture.

- [ ] **Step 1: Types**

```ts
export type Quality = 'preview' | 'low' | 'high' | 'lossless' | 'hires';
export interface AssetInfo { key: string; codec: 'flac' | 'opus'; sample_rate: number; bit_depth: number; bitrate_kbps: number; bytes: number }
export interface TrackDoc {
  _id: string; fp_id: number; album_id: string; artist_id: string; title: string; track_no: number;
  duration_ms: number; isrc: string; explicit: boolean; genres: string[]; loudness_lufs: number;
  free_full_length: boolean; assets: Partial<Record<Quality, AssetInfo>>; embedding: number[];
  rights_holder_id: string; created_at: Date;
}
export interface AlbumDoc { _id: string; artist_id: string; title: string; year: number; cover_key: string; upc: string; genres: string[]; created_at: Date }
export interface ArtistDoc { _id: string; name: string; slug: string; bio: string; image_key: string; rights_holder_id: string; created_at: Date }
```

- [ ] **Step 2: Failing tests**

`tests/catalog.test.ts` seeds 2 artists, 2 albums, 4 tracks via `seedCatalog()`, then asserts:
- `GET /api/albums/:id` returns the album with its tracks ordered by `track_no`, and no `assets` keys or `embedding` in the response.
- `GET /api/artists/:id` returns albums and top tracks.
- `GET /api/search?q=night` returns the track titled "Midnight Drive" and no more than 20 per group.
- `GET /api/tracks/missing` returns 404 `not_found`.

- [ ] **Step 3: Implement**

`routes/catalog.ts` mounted at `/api` behind `authMiddleware`. Search uses `$text` on each collection with `{ score: { $meta: 'textScore' } }` sort, limit 20; `q` validated `z.string().trim().min(1).max(80)`. Projections always exclude `embedding` and `assets.*.key`. Text indexes are created by `ingest/ensure-indexes.ts` (Task 1.4) and by the test setup.

- [ ] **Step 4: Run, commit**

Run: `npm test -- catalog`
Expected: all pass.

```bash
git add service && git commit -m "feat(service): catalog reads and search"
```

### Task 1.3: R2 presigning, quality gate, stream and download URLs

**Files:**
- Create: `service/src/helpers/r2.ts`, `service/src/helpers/quality-gate.ts`, `service/src/routes/audio.ts`, `service/tests/quality-gate.test.ts`, `service/tests/audio.test.ts`

**Interfaces:**
- Consumes: `TrackDoc`, `Quality` (Task 1.2), `entitlementFor` (stubbed until Task 4.1).
- Produces: `presignGet(key, expiresInSeconds): Promise<string>`, `allowedQuality(requested, tier, track): Quality | null`.

- [ ] **Step 1: Failing pure test**

```ts
import { describe, it, expect } from 'vitest';
import { allowedQuality } from '../src/helpers/quality-gate.ts';

const asset = { key: 'k', codec: 'flac' as const, sample_rate: 96000, bit_depth: 24, bitrate_kbps: 3000, bytes: 1 };
const full = { free_full_length: false, assets: { hires: asset, lossless: asset, high: asset, low: asset, preview: asset } };
const cdOnly = { free_full_length: true, assets: { lossless: asset, high: asset, low: asset, preview: asset } };

describe('allowedQuality', () => {
  it.each([
    ['hires', 'premium', full, 'hires'],
    ['hires', 'premium', cdOnly, 'lossless'],
    ['high', 'premium', full, 'high'],
    ['hires', 'free', full, 'preview'],
    ['hires', 'free', cdOnly, 'low'],
  ] as const)('%s for %s', (requested, tier, track, expected) => {
    expect(allowedQuality(requested, tier, track)).toBe(expected);
  });

  it('returns null when nothing playable exists', () => {
    expect(allowedQuality('hires', 'free', { free_full_length: false, assets: {} })).toBeNull();
  });
});
```

- [ ] **Step 2: Run to see it fail**

Run: `npm test -- quality-gate`
Expected: FAIL, module not found.

- [ ] **Step 3: Implement**

`quality-gate.ts`:
```ts
import type { Quality, TrackDoc } from '#types/catalog';
import type { Tier } from '#types/billing';

const LADDER: Quality[] = ['hires', 'lossless', 'high', 'low'];

export function allowedQuality(
  requested: Quality,
  tier: Tier,
  track: Pick<TrackDoc, 'free_full_length' | 'assets'>,
): Quality | null {
  if (tier === 'free') {
    if (track.free_full_length && track.assets.low) return 'low';
    return track.assets.preview ? 'preview' : null;
  }
  if (requested === 'preview') return track.assets.preview ? 'preview' : null;
  const fromIndex = LADDER.indexOf(requested);
  return LADDER.slice(fromIndex).find(q => track.assets[q]) ?? null;
}
```

`types/billing.ts` (created here, extended in Task 4.1): `export type Tier = 'free' | 'premium';`

`r2.ts`:
```ts
import { S3Client, GetObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { env } from '#helpers/env';

let client: S3Client | undefined;

function r2(): S3Client {
  const { accountId, accessKeyId, secretAccessKey } = env.r2;
  client ??= new S3Client({
    region: 'auto',
    endpoint: `https://${accountId}.r2.cloudflarestorage.com`,
    credentials: { accessKeyId, secretAccessKey },
  });
  return client;
}

export function presignGet(key: string, expiresInSeconds: number): Promise<string> {
  return getSignedUrl(r2(), new GetObjectCommand({ Bucket: env.r2.bucket, Key: key }), { expiresIn: expiresInSeconds });
}
```

`routes/audio.ts`: `GET /tracks/:id/stream` and `GET /tracks/:id/download`, query `z.object({ quality: z.enum(['preview','low','high','lossless','hires']) }).strict()`. Download returns 403 `premium_required` for free users. Response:
```ts
{ url, quality, codec, sample_rate, bit_depth, loudness_lufs, expires_at }
```
Stream expiry 900 s, download 21,600 s.

`audio.test.ts` mocks `#helpers/r2` with `vi.mock` returning `https://r2.test/<key>` and checks: premium gets `hires`, free gets `preview`, free download is 403.

- [ ] **Step 4: Run, commit**

Run: `npm test`
Expected: all pass.

```bash
git add service && git commit -m "feat(service): quality gate and presigned audio urls"
```

### Task 1.4: Ingest CLI

**Files:**
- Create: `service/ingest/ingest-album.ts`, `service/ingest/ffmpeg.ts`, `service/ingest/ensure-indexes.ts`, `service/ingest/remove-track.ts`, `service/ingest/README.md`, `service/tests/ffmpeg-args.test.ts`

**Interfaces:**
- Consumes: `TrackDoc`, `AlbumDoc`, `ArtistDoc`, `landmarks()` from Task 7.1 (fingerprint step is added in Task 7.2; this task leaves `fp_id` assigned but writes no fingerprints).
- Produces: `tierArgs(quality, source: { sampleRate, bitDepth, durationMs }, input, output): string[] | null`.

- [ ] **Step 1: Failing test for the transcoding ladder**

```ts
import { describe, it, expect } from 'vitest';
import { tierArgs } from '../ingest/ffmpeg.ts';

const hires = { sampleRate: 352800, bitDepth: 24, durationMs: 240000 };
const cd = { sampleRate: 44100, bitDepth: 16, durationMs: 240000 };

describe('tierArgs', () => {
  it('caps hires at 192 kHz with soxr', () => {
    expect(tierArgs('hires', hires, 'in.flac', 'out.flac')!.join(' ')).toContain('aresample=192000:resampler=soxr');
  });
  it('skips hires for CD sources', () => {
    expect(tierArgs('hires', cd, 'in.flac', 'out.flac')).toBeNull();
  });
  it('dithers lossless to 16/44.1', () => {
    const args = tierArgs('lossless', hires, 'in.flac', 'out.flac')!.join(' ');
    expect(args).toContain('aresample=44100:resampler=soxr:dither_method=triangular');
    expect(args).toContain('-sample_fmt s16');
  });
  it('cuts a 30 s preview from 25 %', () => {
    const args = tierArgs('preview', cd, 'in.flac', 'out.opus')!;
    expect(args).toEqual(expect.arrayContaining(['-ss', '60', '-t', '30', '-b:a', '96k']));
  });
});
```

- [ ] **Step 2: Run to see it fail, then implement**

`ingest/ffmpeg.ts`:
```ts
import type { Quality } from '#types/catalog';

export interface SourceInfo { sampleRate: number; bitDepth: number; durationMs: number }

export function tierArgs(quality: Quality, src: SourceInfo, input: string, output: string): string[] | null {
  const base = ['-hide_banner', '-y', '-i', input, '-map', '0:a:0', '-map_metadata', '-1'];
  switch (quality) {
    case 'hires': {
      if (src.sampleRate <= 48000 && src.bitDepth <= 16) return null;
      const rate = Math.min(src.sampleRate, 192000);
      return [...base, '-af', `aresample=${rate}:resampler=soxr`, '-c:a', 'flac', '-sample_fmt', 's32', '-bits_per_raw_sample', '24', '-compression_level', '8', output];
    }
    case 'lossless':
      return [...base, '-af', 'aresample=44100:resampler=soxr:dither_method=triangular', '-c:a', 'flac', '-sample_fmt', 's16', '-compression_level', '8', output];
    case 'high':
      return [...base, '-c:a', 'libopus', '-b:a', '160k', output];
    case 'low':
      return [...base, '-c:a', 'libopus', '-b:a', '64k', output];
    case 'preview': {
      const start = Math.floor(src.durationMs / 4 / 1000);
      return ['-hide_banner', '-y', '-ss', String(start), '-t', '30', '-i', input, '-map', '0:a:0', '-c:a', 'libopus', '-b:a', '96k', output];
    }
  }
}
```

The test expects `-ss 60` for a 240 s CD track: 240000/4/1000 = 60. Correct.

- [ ] **Step 3: ingest-album.ts flow**

```
parse args --dir --artist --rights
read album.json { title, year, upc, genres[], tracks: [{ file, title, isrc, explicit, free_full_length }] }
for each track:
  probe = ffprobe -v error -show_entries stream=sample_rate,bits_per_raw_sample,codec_name:format=duration -of json
  reject codec not in [flac, dsd_lsbf_planar, dsd_msbf_planar]  -> exit 1 with file name
  if dsd: ffmpeg -i x.dsf -af aresample=176400:resampler=soxr -c:a flac -sample_fmt s32 -bits_per_raw_sample 24 tmp.flac
  for q in [hires, lossless, high, low, preview]: args = tierArgs(...); if args run ffmpeg
  lufs = parse "I:" from ffmpeg -i src -af ebur128 -f null -  (stderr, last "I:  -x.x LUFS")
  upload each output with PutObjectCommand to audio/<track_id>/<q>.<flac|opus>
  fp_id = findOneAndUpdate counters {_id:'fp_id'} {$inc:{seq:1}} upsert returnDocument after
  upsert catalog_tracks with assets map
upload cover.jpg to covers/<album_id>.jpg; upsert album and artist
```

Run child processes with `execFile` (never a shell string; file names come from disk). `ensure-indexes.ts` creates every index from spec section 8.2 except the Atlas vector index (created in the Atlas UI in Task 6.1). `remove-track.ts --track <id>` deletes R2 objects, the track doc, its fingerprints and neighbour entries.

- [ ] **Step 4: Manual check with one CC BY album**

Run: `npx tsx ingest/ensure-indexes.ts && npx tsx ingest/ingest-album.ts --dir ./drops/test-artist/test-album --artist <id> --rights <id>`
Expected: R2 shows 5 objects per hi-res track, Mongo has the track with `assets.hires.sample_rate` set.

- [ ] **Step 5: Commit**

```bash
git add service && git commit -m "feat(ingest): album ingest with hi-res tier ladder"
```

---

## Phase 2: Android playback

### Task 2.1: API client, auth store, sign-in and onboarding

**Files:**
- Create: `android/app/src/main/java/com/anish1050/hires/data/api/{HiResApi,Dto,AuthStore}.kt`, `.../data/repo/{CatalogRepository,EntitlementRepository}.kt`, `.../ui/onboarding/{OnboardingScreen,OnboardingViewModel}.kt`
- Modify: `AppContainer.kt`, `MainActivity.kt`

**Interfaces:**
- Produces: `HiResApi` with suspend functions `signInGoogle(idToken)`, `me()`, `home()`, `search(q)`, `album(id)`, `artist(id)`, `streamUrl(id, quality)` (blocking variant `streamUrlBlocking` for the data source thread), `downloadUrlBlocking(id, quality)`, `postPlays(events)`, `stats(period)`, `discover()`, `library(kind)`, `save(kind,id)`, `unsave(kind,id)`, `recognize(pcm: ByteArray)`, `verifyPurchase(token)`, `deleteMe()`, `setTaste(genres, artistIds)`. DTOs mirror spec 8.3 with `@SerialName` snake_case. `AuthStore`: `token`, `deviceId`, `downloadKey: ByteArray` (16 bytes, created once), `entitlementJson`. `EntitlementRepository.current: StateFlow<Entitlement>`, `refresh()`, `canPlayOffline(now: Instant): Boolean`.

- [ ] **Step 1: Offline gate test (JVM)**

`app/src/test/java/com/anish1050/hires/EntitlementTest.kt`:
```kotlin
class EntitlementTest {
    private val now = Instant.parse("2026-11-01T00:00:00Z")

    @Test fun premiumWithinBothWindowsPlaysOffline() {
        val e = Entitlement(Tier.PREMIUM, expiresAt = now.plus(Duration.ofDays(10)), verifiedAt = now.minus(Duration.ofDays(1)))
        assertThat(e.canPlayOffline(now)).isTrue()
    }
    @Test fun notVerifiedFor30DaysBlocks() {
        val e = Entitlement(Tier.PREMIUM, expiresAt = now.plus(Duration.ofDays(300)), verifiedAt = now.minus(Duration.ofDays(31)))
        assertThat(e.canPlayOffline(now)).isFalse()
    }
    @Test fun expiredMoreThan7DaysBlocks() {
        val e = Entitlement(Tier.PREMIUM, expiresAt = now.minus(Duration.ofDays(8)), verifiedAt = now.minus(Duration.ofDays(1)))
        assertThat(e.canPlayOffline(now)).isFalse()
    }
    @Test fun freeNeverPlaysOffline() {
        assertThat(Entitlement.FREE.canPlayOffline(now)).isFalse()
    }
}
```

- [ ] **Step 2: Implement Entitlement**

```kotlin
enum class Tier { FREE, PREMIUM }

data class Entitlement(val tier: Tier, val expiresAt: Instant?, val verifiedAt: Instant?) {
    fun canPlayOffline(now: Instant): Boolean {
        if (tier != Tier.PREMIUM || expiresAt == null || verifiedAt == null) return false
        val limit = minOf(expiresAt.plus(Duration.ofDays(7)), verifiedAt.plus(Duration.ofDays(30)))
        return now.isBefore(limit)
    }
    companion object { val FREE = Entitlement(Tier.FREE, null, null) }
}
```

Requires `coreLibraryDesugaring` for `java.time` on API 27? No: `java.time` is available from API 26. No desugaring needed.

- [ ] **Step 3: API client and auth**

`HiResApi` follows the Expense Tracker's `LedgerApi`: one `OkHttpClient` (timeouts 15 s, `recognize` 30 s), `Json { ignoreUnknownKeys = true }`, envelope unwrapped, `ApiException(status, code)` on failure, 401 clears `AuthStore.token`. Sign-in uses Credential Manager `GetGoogleIdOption(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)` exactly as in the Expense Tracker.

Onboarding: sign-in button → genre chips (from `GET /api/home` `featured` genres; fallback list Lo-fi, Pop, R&B, Indie, Electronic, Jazz, Classical, Hip-hop) → artist grid (from search results of chosen genres) → `PATCH /api/me/taste`. The service route for `PATCH /api/me/taste` is added in this task: `z.object({ genres: z.array(z.string()).min(1).max(10), artist_ids: z.array(z.string()).max(20) }).strict()`, updates `core_users.taste_genres` / `taste_artist_ids`, with a route test.

- [ ] **Step 4: Run and commit**

Run: `gradle testDebugUnitTest` and `npm test -- me`
Expected: pass.

```bash
git add android service && git commit -m "feat: sign-in, onboarding taste, entitlement offline gate"
```

### Task 2.2: Playback service, float output, stream resolving, quality policy

**Files:**
- Create: `.../playback/{PlaybackService,PlayerFactory,QualityPolicy,TrackUris}.kt`, `app/src/test/.../QualityPolicyTest.kt`
- Modify: `AndroidManifest.xml` (service), `AppContainer.kt`

**Interfaces:**
- Consumes: `HiResApi.streamUrlBlocking`, `downloadUrlBlocking`, `DownloadStore.playerDataSourceFactory` (Task 3.1; until then the player uses the resolving OkHttp factory directly).
- Produces: `QualityPolicy.choose(isPremium, prefs, network, output): Quality`, `TrackUris.stream(id)`, `TrackUris.offline(id)`, `TrackUris.download(id, quality)`, `TrackUris.cacheKey(id) = "track:$id"`, `buildPlayer(context, dataSourceFactory): ExoPlayer`.

- [ ] **Step 1: QualityPolicy test**

```kotlin
class QualityPolicyTest {
    private val prefs = QualityPrefs()

    @Test fun freeUserGetsLow() =
        assertThat(QualityPolicy.choose(false, prefs, Network.WIFI, Output.USB_DAC)).isEqualTo(Quality.LOW)
    @Test fun wifiPremiumGetsHires() =
        assertThat(QualityPolicy.choose(true, prefs, Network.WIFI, Output.WIRED)).isEqualTo(Quality.HIRES)
    @Test fun cellularDefaultsToLossless() =
        assertThat(QualityPolicy.choose(true, prefs, Network.CELLULAR, Output.WIRED)).isEqualTo(Quality.LOSSLESS)
    @Test fun bluetoothCapsAtLossless() =
        assertThat(QualityPolicy.choose(true, prefs, Network.WIFI, Output.BLUETOOTH)).isEqualTo(Quality.LOSSLESS)
    @Test fun bluetoothOptInKeepsHires() =
        assertThat(QualityPolicy.choose(true, prefs.copy(bluetoothHiRes = true), Network.WIFI, Output.BLUETOOTH)).isEqualTo(Quality.HIRES)
}
```

- [ ] **Step 2: Implement QualityPolicy**

```kotlin
enum class Quality(val wire: String) { PREVIEW("preview"), LOW("low"), HIGH("high"), LOSSLESS("lossless"), HIRES("hires") }
enum class Network { WIFI, CELLULAR, OFFLINE }
enum class Output { BUILT_IN, WIRED, USB_DAC, BLUETOOTH }

data class QualityPrefs(
    val wifi: Quality = Quality.HIRES,
    val cellular: Quality = Quality.LOSSLESS,
    val bluetoothHiRes: Boolean = false,
)

object QualityPolicy {
    fun choose(isPremium: Boolean, prefs: QualityPrefs, network: Network, output: Output): Quality {
        if (!isPremium) return Quality.LOW // server answers preview when the track is not free full-length
        val wanted = if (network == Network.CELLULAR) prefs.cellular else prefs.wifi
        val bluetoothCapped = output == Output.BLUETOOTH && !prefs.bluetoothHiRes
        return if (bluetoothCapped) minOf(wanted, Quality.LOSSLESS) else wanted
    }
}
```

- [ ] **Step 3: Player and resolving data source**

`PlayerFactory.kt`:
```kotlin
fun buildPlayer(context: Context, dataSourceFactory: DataSource.Factory): ExoPlayer {
    val renderers = DefaultRenderersFactory(context).setEnableAudioFloatOutput(true)
    val attrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setAudioAttributes(attrs, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
}

fun resolvingFactory(api: HiResApi, upstream: DataSource.Factory, quality: () -> Quality): DataSource.Factory =
    ResolvingDataSource.Factory(upstream) { spec ->
        val uri = spec.uri
        if (uri.scheme != "app") return@Factory spec
        val id = uri.lastPathSegment ?: return@Factory spec
        val resolved = when (uri.host) {
            "download" -> api.downloadUrlBlocking(id, uri.getQueryParameter("quality") ?: Quality.HIRES.wire)
            else -> api.streamUrlBlocking(id, quality().wire) // "stream", and "offline" when the cache misses
        }
        spec.withUri(Uri.parse(resolved.url))
    }
```

`upstream` = `OkHttpDataSource.Factory(okHttpClient)`. The resolver runs on ExoPlayer's loading thread, so a blocking call is correct there.

`PlaybackService` extends `MediaSessionService`: builds the player in `onCreate`, wraps it in `MediaSession`, releases in `onDestroy`, returns the session in `onGetSession`. Manifest: `<service android:name=".playback.PlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="true"><intent-filter><action android:name="androidx.media3.session.MediaSessionService"/></intent-filter></service>`.

The UI connects with `MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()` held in `AppContainer.playerConnection`.

Building `MediaItem`s: `MediaItem.Builder().setMediaId(id).setUri(TrackUris.stream(id)).setMediaMetadata(title, artist, artworkUri)`. Offline items additionally `setCustomCacheKey(TrackUris.cacheKey(id))` and use `TrackUris.offline(id)`.

- [ ] **Step 4: Run tests, play one track, commit**

Run: `gradle testDebugUnitTest`, then on a phone with `service` running locally and one ingested track, play it.
Expected: tests pass; audio plays; logcat shows `onAudioTrackInitialized` encoding 4 for a hi-res track.

```bash
git add android && git commit -m "feat(android): media3 playback with float output and resolved stream urls"
```

### Task 2.3: Signal path badge, USB DAC rate matching, loudness

**Files:**
- Create: `.../playback/{SignalPath,UsbMixer}.kt`, `app/src/test/.../SignalPathTest.kt`
- Modify: `PlaybackService.kt`

**Interfaces:**
- Produces: `SignalPath.state: StateFlow<SignalChain>`, `SignalChain(sourceCodec, sourceRate, sourceBits, outputEncoding, outputRate, device)`, `SignalChain.label(): String`, `UsbMixer.matchTrack(sampleRate): Boolean`, `UsbMixer.clear()`, `loudnessVolume(lufs: Float, enabled: Boolean): Float`.

- [ ] **Step 1: Label and loudness tests**

```kotlin
class SignalPathTest {
    @Test fun usbChainIsSpelledOut() {
        val chain = SignalChain("FLAC", 96000, 24, "float", 96000, "USB DAC")
        assertThat(chain.label()).isEqualTo("FLAC 24/96 → float → 96 kHz → USB DAC")
    }
    @Test fun builtInResampleIsVisible() {
        val chain = SignalChain("FLAC", 96000, 24, "float", 48000, "Phone speaker")
        assertThat(chain.label()).isEqualTo("FLAC 24/96 → float → 48 kHz (resampled) → Phone speaker")
    }
    @Test fun loudnessOffIsUnity() = assertThat(loudnessVolume(-8f, enabled = false)).isEqualTo(1f)
    @Test fun loudTrackIsTurnedDown() = assertThat(loudnessVolume(-8f, enabled = true)).isWithin(0.001f).of(0.501f)
    @Test fun quietTrackIsNeverBoosted() = assertThat(loudnessVolume(-20f, enabled = true)).isEqualTo(1f)
}
```

- [ ] **Step 2: Implement**

```kotlin
data class SignalChain(
    val sourceCodec: String, val sourceRate: Int, val sourceBits: Int,
    val outputEncoding: String, val outputRate: Int, val device: String,
) {
    fun label(): String {
        val khz = { hz: Int -> if (hz % 1000 == 0) "${hz / 1000}" else "%.1f".format(hz / 1000.0) }
        val resampled = if (outputRate != sourceRate) " (resampled)" else ""
        return "$sourceCodec $sourceBits/${khz(sourceRate)} → $outputEncoding → ${khz(outputRate)} kHz$resampled → $device"
    }
}

fun loudnessVolume(lufs: Float, enabled: Boolean): Float =
    if (!enabled) 1f else min(1f, 10f.pow((-14f - lufs) / 20f))
```

`SignalPath` is an `AnalyticsListener` that fills `SignalChain` from `onAudioInputFormatChanged` (`Format.sampleMimeType` → "FLAC"/"Opus", `sampleRate`, bits from the stream response's `bit_depth` carried in `MediaMetadata.extras`) and `onAudioTrackInitialized` (`config.encoding` → "float"/"16-bit"/"24-bit", `config.sampleRate`). The device name comes from `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` mapped: `TYPE_USB_DEVICE/TYPE_USB_HEADSET` → "USB DAC", `TYPE_WIRED_HEADPHONES/TYPE_WIRED_HEADSET` → "Wired", `TYPE_BLUETOOTH_A2DP` → "Bluetooth", else "Phone speaker". Re-read on `AudioDeviceCallback`.

`UsbMixer.kt`:
```kotlin
@RequiresApi(34)
class UsbMixer(private val audioManager: AudioManager) {
    private val attrs = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun usbOutput(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

    fun matchTrack(sampleRate: Int): Boolean {
        val device = usbOutput() ?: return false
        val best = audioManager.getSupportedMixerAttributes(device)
            .filter { it.format.sampleRate == sampleRate && it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT }
            .maxByOrNull { bitDepth(it.format.encoding) } ?: return false
        return audioManager.setPreferredMixerAttributes(attrs, device, best)
    }

    fun clear() {
        usbOutput()?.let { audioManager.clearPreferredMixerAttributes(attrs, it) }
    }

    private fun bitDepth(encoding: Int) = when (encoding) {
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_16BIT -> 16
        else -> 0
    }
}
```

`PlaybackService` calls `matchTrack(rate)` in `onAudioInputFormatChanged` when the setting "USB DAC rate matching" is on and `Build.VERSION.SDK_INT >= 34`, and `clear()` in `onDestroy`. It sets `player.volume = loudnessVolume(lufs, setting)` on every `onMediaItemTransition`, reading `loudness_lufs` from `MediaMetadata.extras`.

- [ ] **Step 3: Run tests, check on a phone with a USB DAC, commit**

Run: `gradle testDebugUnitTest`
Expected: pass. On Android 14 with a USB DAC, a 96 kHz track shows "→ 96 kHz → USB DAC" without "(resampled)".

```bash
git add android && git commit -m "feat(android): signal path badge, usb dac rate matching, loudness"
```

### Task 2.4: Home (Featured), Library, Album, Artist, Search, Now Playing

**Files:**
- Create: `.../ui/home/{HomeScreen,HomeViewModel,CoverCarousel,DialScrubber}.kt`, `.../ui/library/{LibraryScreen,LibraryViewModel,AiDiscoveryCard}.kt`, `.../ui/album/AlbumScreen.kt`, `.../ui/artist/ArtistScreen.kt`, `.../ui/search/{SearchScreen,SearchViewModel}.kt`, `.../ui/player/{NowPlayingSheet,MiniPlayer,SignalPathBadge}.kt`, `.../data/repo/LibraryRepository.kt`, `service/src/routes/library.ts`, `service/src/routes/home.ts`, `service/tests/library.test.ts`
- Modify: `MainActivity.kt`, `Nav.kt`, `service/src/app.ts`

**Interfaces:**
- Consumes: `HiResApi`, `AppContainer.playerConnection`, `SignalPath.state`, theme tokens and `GlassCard`.
- Produces: `GET /api/home` (until Task 6.2 `daily_mixes` is the editorial playlists and `discover_preview` is `{count: 0, match_percent: 0, sparkline: []}`), library routes per spec 8.3.

- [ ] **Step 1: Library route test (service)**

`tests/library.test.ts`: save a track → `GET /api/library?kind=track` returns it → saving twice keeps one row → `DELETE /api/library/track/:id` removes it → unknown `kind` returns 400.

- [ ] **Step 2: Implement routes**

`library.ts`: `kind` = `z.enum(['track','album','artist','playlist'])`; POST body `z.object({ kind, item_id: z.string().min(1) }).strict()`; upsert on `{user_id, kind, item_id}`. GET joins the item docs with `$lookup` by kind and returns views. `home.ts`: `featured` = editorial playlist `featured` tracks; `recent` = last 20 distinct `track_id` from `play_events`; `daily_mixes` = editorial playlists.

- [ ] **Step 3: Screens (match the mockup)**

- `HomeScreen`: background = current mix cover via Coil with `Modifier.blur(40.dp)` and an `Ink` vertical gradient on top; header `SectionHeader("Featured", "Sounds")` + collapse chevron; "DAILY MIX" overline (12 sp, letter spacing 4) and title in two lines (48 sp bold, second line `TextMuted`); `CoverCarousel` = `HorizontalPager` with `pageSize = PageSize.Fixed(96.dp)`, `contentPadding` centring the current page, circular crop, current page scaled 1.3 via `graphicsLayer` from `currentPageOffsetFraction`; `DialScrubber` = `Canvas` arc of 60 ticks across 180°, current position as a white 2 dp needle, drag gestures seek; previous / next icons; bottom row of three 48 dp circles (search, play/pause, save).
- `LibraryScreen`: `SectionHeader("Music", "Library")` with search + avatar; `LazyColumn` of `GlassCard` rows (88 dp cover with 16 dp radius, title 16 sp bold, subtitle 13 sp muted, artist chip = 20 dp avatar + name in a `Graphite` pill, `NorthEast` arrow icon); `AiDiscoveryCard` = `AiGradient` card, "AI Discovery" + chevron, big count (48 sp) + "tracks", "AI Match 96%", sparkline of 16 dots with arcs drawn on `Canvas` from `discover_preview.sparkline`.
- `MiniPlayer` above the bottom bar; tapping opens `NowPlayingSheet` (`ModalBottomSheet`, full height) with cover, title, artist, `DialScrubber`, controls, `SignalPathBadge` (mono 12 sp text in a `Graphite` pill), like, download, queue.
- `SearchScreen`: text field, 300 ms debounce with `debounce` on a `MutableStateFlow`, three result sections.
- `AlbumScreen` / `ArtistScreen`: header cover, play all, track list rows.

Use `@Preview` composables with fake data for each screen so they can be checked without the service.

- [ ] **Step 4: Run, compare to the mockup, commit**

Run: `npm test -- library` and `gradle assembleDebug`
Expected: tests pass; screens visually match the middle and right mockup phones and `docs/ui-preview.html`.

```bash
git add android service && git commit -m "feat: featured, library, search, album, artist, now playing"
```

---

## Phase 3: Encrypted downloads

### Task 3.1: Encrypted download cache

**Files:**
- Create: `.../download/DownloadStore.kt`, `app/src/test/.../DownloadStoreTest.kt`
- Modify: `AppContainer.kt`, `PlaybackService.kt` (use `playerDataSourceFactory`)

**Interfaces:**
- Consumes: `AuthStore.downloadKey`, `resolvingFactory` (Task 2.2).
- Produces: `DownloadStore(context, key, upstream)` with `cache: SimpleCache`, `databaseProvider`, `downloaderDataSourceFactory: CacheDataSource.Factory` (writes encrypted), `playerDataSourceFactory: CacheDataSource.Factory` (reads encrypted, never writes).

- [ ] **Step 1: Robolectric round-trip test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class DownloadStoreTest {
    @Test fun bytesRoundTripEncrypted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plain = ByteArray(64 * 1024) { (it % 251).toByte() }
        val upstream = FakeDataSource.Factory().apply { fakeDataSet.setData("app://download/t1", plain) }
        val store = DownloadStore(context, ByteArray(16) { 7 }, upstream)
        val spec = DataSpec.Builder().setUri("app://download/t1").setKey("track:t1").build()

        val writer = store.downloaderDataSourceFactory.createDataSource()
        writer.open(spec); val sink = ByteArray(plain.size); var read = 0
        while (read < plain.size) { val n = writer.read(sink, read, plain.size - read); if (n < 0) break; read += n }
        writer.close()

        val reader = store.playerDataSourceFactory.createDataSource()
        val back = DataSourceUtil.readToEnd(reader.also { it.open(spec) }); reader.close()
        assertThat(back).isEqualTo(plain)

        val onDisk = File(context.filesDir, "downloads").walk().filter { it.name.endsWith(".exo") }.first().readBytes()
        assertThat(onDisk.take(64)).isNotEqualTo(plain.take(64))
        store.cache.release()
    }
}
```

`FakeDataSource` and `DataSourceUtil` come from `media3-test-utils` / `media3-datasource`; add `testImplementation(libs.media3.test.utils)` and `libs.robolectric`.

- [ ] **Step 2: Implement**

```kotlin
class DownloadStore(context: Context, key: ByteArray, upstream: DataSource.Factory) {
    val databaseProvider = StandaloneDatabaseProvider(context)
    val cache = SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)

    private val encryptedRead = DataSource.Factory { AesCipherDataSource(key, FileDataSource()) }

    val downloaderDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheReadDataSourceFactory(encryptedRead)
        .setCacheWriteDataSinkFactory {
            AesCipherDataSink(key, CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE))
        }

    // Read-only: streamed tracks must never fill storage. Streams have no "track:" key, so they always miss.
    val playerDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheReadDataSourceFactory(encryptedRead)
        .setCacheWriteDataSinkFactory(null)
}
```

`AesCipherDataSink` derives its CTR nonce from the data spec's key and position; `CacheDataSource` passes the same key and absolute position on read and write, which is what makes the round trip work. The test proves it.

`AppContainer`: `downloadStore` built with `resolvingFactory(api, OkHttpDataSource.Factory(okHttp), quality)`. `PlaybackService` builds the player with `downloadStore.playerDataSourceFactory`.

- [ ] **Step 3: Run, commit**

Run: `gradle testDebugUnitTest`
Expected: pass.

```bash
git add android && git commit -m "feat(android): encrypted download cache"
```

### Task 3.2: Download service, repository, Downloads screen

**Files:**
- Create: `.../download/{HiResDownloadService,DownloadsRepository}.kt`, `.../data/db/{HiResDatabase,Entities,Daos}.kt`, `.../ui/downloads/{DownloadsScreen,DownloadsViewModel}.kt`
- Modify: `AndroidManifest.xml`, `AppContainer.kt`, `NowPlayingSheet.kt`, `AlbumScreen.kt`

**Interfaces:**
- Consumes: `DownloadStore`, `TrackUris`, `EntitlementRepository.canPlayOffline`.
- Produces: `DownloadsRepository.download(track: TrackView, quality)`, `remove(trackId)`, `downloaded: Flow<List<DownloadedTrack>>`, `isDownloaded(trackId): Boolean`, Room `downloaded_tracks(track_id PK, title, artist_name, album_title, cover_url, quality, bytes, state, created_at)`.

- [ ] **Step 1: DownloadManager wiring**

```kotlin
val downloadManager: DownloadManager by lazy {
    DownloadManager(
        app,
        DefaultDownloadIndex(downloadStore.databaseProvider),
        DefaultDownloaderFactory(downloadStore.downloaderDataSourceFactory, Executors.newFixedThreadPool(2)),
    ).apply { maxParallelDownloads = 2; requirements = Requirements(Requirements.NETWORK) }
}
```

Never use the `DownloadManager(context, databaseProvider, cache, upstreamFactory, executor)` constructor: it builds its own unencrypted cache data source.

`HiResDownloadService` extends `DownloadService(FOREGROUND_NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, CHANNEL_ID, R.string.downloads_channel, 0)`; `getDownloadManager()` returns the container's; `getScheduler()` returns `null` (WorkManager scheduler not needed in phase 1); notification via `DownloadNotificationHelper`. Manifest: `foregroundServiceType="dataSync"`, `exported="false"`.

- [ ] **Step 2: Repository**

`download(track, quality)`: insert Room row with `state = QUEUED`, then
```kotlin
val request = DownloadRequest.Builder(track.id, TrackUris.download(track.id, quality))
    .setCustomCacheKey(TrackUris.cacheKey(track.id))
    .build()
DownloadService.sendAddDownload(context, HiResDownloadService::class.java, request, false)
```
A `DownloadManager.Listener` updates Room `state` (COMPLETED / FAILED) and `bytes`. Failed downloads (expired presigned URL after 6 h) are retried once by re-sending the same request; the resolver fetches a fresh URL.

Queue building: if `isDownloaded(id)` and the network is offline or the user is on mobile data with "prefer downloads", use `TrackUris.offline(id)` with the cache key. Offline items are blocked with a snackbar when `canPlayOffline(now)` is false.

- [ ] **Step 3: UI**

Download button on Now Playing and Album (download all). Downloads screen: list with size and quality chip, total storage used, swipe to remove, "Remove all". Free users tapping download get the Paywall.

- [ ] **Step 4: Manual check and commit**

Expected on a phone: download a track, enable airplane mode, it plays; files under `files/downloads` are unreadable as FLAC (`adb shell run-as com.anish1050.hires` + `head -c 4` is not `fLaC`).

```bash
git add android && git commit -m "feat(android): offline downloads with entitlement gate"
```

---

## Phase 4: Subscriptions

### Task 4.1: Entitlement and Play verification on the server

**Files:**
- Create: `service/src/helpers/entitlement.ts`, `service/src/helpers/play-developer-api.ts`, `service/src/routes/billing.ts`, `service/tests/entitlement.test.ts`, `service/tests/billing-verify.test.ts`
- Modify: `service/src/types/billing.ts`, `service/src/routes/me.ts`, `service/src/routes/audio.ts`, `service/src/app.ts`

**Interfaces:**
- Produces: `tierFor(sub: SubscriptionDoc | null, now: Date): Tier`, `entitlementFor(userId, now): Promise<{tier, expires_at, verified_at}>`, `getSubscription(token): Promise<PlaySubscriptionV2>`, `acknowledge(productId, token): Promise<void>`, `upsertFromPlay(userId, token, state): Promise<SubscriptionDoc>`.

- [ ] **Step 1: Failing entitlement test**

```ts
import { describe, it, expect } from 'vitest';
import { tierFor } from '../src/helpers/entitlement.ts';

const now = new Date('2026-11-01T00:00:00Z');
const future = new Date('2026-12-01T00:00:00Z');
const past = new Date('2026-10-01T00:00:00Z');
const sub = (state: string, expires_at: Date) => ({ state, expires_at });

describe('tierFor', () => {
  it.each([
    ['SUBSCRIPTION_STATE_ACTIVE', future, 'premium'],
    ['SUBSCRIPTION_STATE_IN_GRACE_PERIOD', future, 'premium'],
    ['SUBSCRIPTION_STATE_CANCELED', future, 'premium'],
    ['SUBSCRIPTION_STATE_CANCELED', past, 'free'],
    ['SUBSCRIPTION_STATE_ON_HOLD', future, 'free'],
    ['SUBSCRIPTION_STATE_EXPIRED', future, 'free'],
  ])('%s', (state, expires, tier) => {
    expect(tierFor(sub(state, expires), now)).toBe(tier);
  });
  it('no subscription is free', () => expect(tierFor(null, now)).toBe('free'));
});
```

- [ ] **Step 2: Implement**

`types/billing.ts`:
```ts
export type Tier = 'free' | 'premium';
export interface SubscriptionDoc {
  _id: string; provider: 'google_play'; purchase_token: string; product_id: string; base_plan_id: string;
  state: string; expires_at: Date; auto_renewing: boolean; verified_at: Date;
}
export interface PlaySubscriptionV2 {
  subscriptionState: string;
  acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' | 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED';
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
  lineItems: { productId: string; expiryTime: string; autoRenewingPlan?: { autoRenewEnabled?: boolean }; offerDetails?: { basePlanId?: string } }[];
}
```

`entitlement.ts`:
```ts
const PAID_UP = new Set(['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED']);

export function tierFor(sub: Pick<SubscriptionDoc, 'state' | 'expires_at'> | null, now: Date): Tier {
  return sub !== null && PAID_UP.has(sub.state) && sub.expires_at > now ? 'premium' : 'free';
}
```
`entitlementFor` loads `subscriptions` by `_id = userId` and returns `{ tier, expires_at, verified_at }`.

`play-developer-api.ts`:
```ts
import { SignJWT, importPKCS8 } from 'jose';
import { env } from '#helpers/env';
import type { PlaySubscriptionV2 } from '#types/billing';

let cached: { token: string; expiresAt: number } | undefined;

async function accessToken(): Promise<string> {
  if (cached && cached.expiresAt > Date.now() + 60_000) return cached.token;
  const sa = JSON.parse(env.playServiceAccountJson) as { client_email: string; private_key: string };
  const assertion = await new SignJWT({ scope: 'https://www.googleapis.com/auth/androidpublisher' })
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuer(sa.client_email)
    .setAudience('https://oauth2.googleapis.com/token')
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(await importPKCS8(sa.private_key, 'RS256'));
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  });
  if (!res.ok) throw new Error(`[play:token] ${res.status}`);
  const body = (await res.json()) as { access_token: string; expires_in: number };
  cached = { token: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
  return cached.token;
}

const base = () => `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${env.playPackageName}`;

export async function getSubscription(token: string): Promise<PlaySubscriptionV2> {
  const res = await fetch(`${base()}/purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`, {
    headers: { authorization: `Bearer ${await accessToken()}` },
  });
  if (!res.ok) throw new Error(`[play:get] ${res.status}`);
  return (await res.json()) as PlaySubscriptionV2;
}

export async function acknowledge(productId: string, token: string): Promise<void> {
  const res = await fetch(`${base()}/purchases/subscriptions/${productId}/tokens/${encodeURIComponent(token)}:acknowledge`, {
    method: 'POST',
    headers: { authorization: `Bearer ${await accessToken()}` },
  });
  if (!res.ok) throw new Error(`[play:ack] ${res.status}`);
}
```

`upsertFromPlay(userId, token, state)`: latest line item = max `expiryTime`; `UpdateOne('subscriptions', { _id: userId }, { $set: { provider, purchase_token: token, product_id, base_plan_id, state: state.subscriptionState, expires_at: new Date(expiryTime), auto_renewing, verified_at: new Date() } }, { upsert: true })`. Acknowledge when pending.

`routes/billing.ts` `POST /google/verify` (auth): body `z.object({ purchase_token: z.string().min(10).max(512) }).strict()`; `getSubscription`; if `obfuscatedExternalAccountId !== userId` → 403 `account_mismatch`; `upsertFromPlay`; return `entitlementFor(userId, now)`.

Replace the `me.ts` and `audio.ts` stubs with `entitlementFor`.

- [ ] **Step 3: Route test with mocked Play API**

`billing-verify.test.ts`: `vi.mock('#helpers/play-developer-api')`. Cases: matching account → 200, tier premium, `acknowledge` called once; mismatched account → 403 and no subscription row; already acknowledged → `acknowledge` not called.

- [ ] **Step 4: Run, commit**

Run: `npm test`
Expected: all pass.

```bash
git add service && git commit -m "feat(service): play subscription verification and entitlement"
```

### Task 4.2: Real-time Developer Notifications

**Files:**
- Create: `service/tests/billing-rtdn.test.ts`
- Modify: `service/src/routes/billing.ts`

**Interfaces:**
- Consumes: `getSubscription`, `upsertFromPlay`.

- [ ] **Step 1: Failing tests**

Cases, with `getSubscription` mocked:
- Wrong `?token=` → 401.
- Test notification (`testNotification` present) → 200, one `billing_events` row.
- Subscription notification → subscription upserted for the user found in `obfuscatedExternalAccountId`.
- Same Pub/Sub `messageId` twice → second returns 200 and `getSubscription` is called once.
- `getSubscription` throws → 500 and `billing_events.error` is set.

Envelope used in tests:
```ts
const envelope = (data: object, messageId = 'm1') => ({
  message: { data: Buffer.from(JSON.stringify(data)).toString('base64'), messageId, publishTime: new Date().toISOString() },
  subscription: 'projects/hires-prod/subscriptions/rtdn',
});
const subNote = { version: '1.0', packageName: 'com.anish1050.hires', eventTimeMillis: '1', subscriptionNotification: { version: '1.0', notificationType: 4, purchaseToken: 'tok-1' } };
```

- [ ] **Step 2: Implement**

```ts
billing.post('/google/rtdn', async c => {
  if (c.req.query('token') !== env.rtdnSecret) return fail(c, 401, 'unauthorized', 'Bad token');
  const body = rtdnEnvelope.parse(await c.req.json());
  const note = rtdnPayload.parse(JSON.parse(Buffer.from(body.message.data, 'base64').toString('utf8')));
  const token = note.subscriptionNotification?.purchaseToken ?? '';
  try {
    await MongoClientInsertOne('billing_events', {
      _id: body.message.messageId, purchase_token: token,
      notification_type: note.subscriptionNotification?.notificationType ?? 0, received_at: new Date(),
    });
  } catch (err) {
    if (isDuplicateKey(err)) return ok(c, { duplicate: true });
    throw err;
  }
  if (!token) return ok(c, { test: true });
  try {
    const state = await getSubscription(token);
    const userId = state.externalAccountIdentifiers?.obfuscatedExternalAccountId;
    if (userId) await upsertFromPlay(userId, token, state);
    await MongoClientUpdateOne('billing_events', { _id: body.message.messageId }, { $set: { processed_at: new Date() } });
    return ok(c, { processed: true });
  } catch (err) {
    const message = err instanceof Error ? err.message : 'unknown';
    await MongoClientUpdateOne('billing_events', { _id: body.message.messageId }, { $set: { error: message } });
    console.error('[billing:rtdn] failed:', message);
    return fail(c, 500, 'rtdn_failed', 'Retry later');
  }
});
```

On a 500 Pub/Sub retries, but the insert will hit a duplicate and return 200. So before treating a duplicate as done, check `processed_at`: if the existing event has no `processed_at`, continue processing instead of returning. Implement `isDuplicateKey(err)` as `err instanceof MongoServerError && err.code === 11000`. `rtdnEnvelope` and `rtdnPayload` are Zod schemas (`.passthrough()` on the Google payload, since Google adds fields; `.strict()` is for our own clients).

- [ ] **Step 3: Run, commit**

Run: `npm test -- billing`
Expected: all pass.

```bash
git add service && git commit -m "feat(service): play rtdn handler, persist-first and idempotent"
```

### Task 4.3: App billing and paywall

**Files:**
- Create: `.../billing/PlayBilling.kt`, `.../ui/paywall/{PaywallScreen,PaywallViewModel}.kt`
- Modify: `AppContainer.kt`, `SettingsScreen.kt` (restore button)

**Interfaces:**
- Consumes: `HiResApi.verifyPurchase`, `EntitlementRepository.refresh`.
- Produces: `PlayBilling.offers: StateFlow<List<Offer>>` (`Offer(basePlanId, offerToken, formattedPrice, billingPeriod, hasTrial)`), `PlayBilling.buy(activity, offer, userId)`, `PlayBilling.restore()`.

- [ ] **Step 1: Implement**

```kotlin
class PlayBilling(context: Context, private val api: HiResApi, private val entitlements: EntitlementRepository, private val scope: CoroutineScope) :
    PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _offers = MutableStateFlow<List<Offer>>(emptyList())
    val offers: StateFlow<List<Offer>> = _offers
    private var details: ProductDetails? = null

    suspend fun load() {
        connect()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId("premium").setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        val result = client.queryProductDetails(params)
        details = result.productDetailsList?.firstOrNull()
        _offers.value = details?.subscriptionOfferDetails.orEmpty().map { it.toOffer() }
    }

    fun buy(activity: Activity, offer: Offer, userId: String) {
        val product = details ?: return
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offer.offerToken).build()
            ))
            .setObfuscatedAccountId(userId)
            .build()
        client.launchBillingFlow(activity, flow)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
            scope.launch { api.verifyPurchase(purchase.purchaseToken); entitlements.refresh() }
        }
    }

    suspend fun restore() {
        connect()
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        result.purchasesList.forEach { api.verifyPurchase(it.purchaseToken) }
        entitlements.refresh()
    }
}
```

`connect()` wraps `startConnection` in `suspendCancellableCoroutine`. Check `billing-ktx` 8.x names: in some 8.x releases `queryProductDetails` returns a result whose list property is `productDetailsList`; adjust to the pinned version's API. `toOffer()` reads `pricingPhases`; a phase with `priceAmountMicros == 0` means trial.

Paywall: two `GlassCard` plan tiles (annual highlighted with "Save 16 %"), trial line, primary button, "Restore purchases", links to terms and privacy. Opened from download taps, hi-res quality selection, and Settings.

- [ ] **Step 2: Test with Play's licence testers**

Upload a signed build to the internal testing track, add your Gmail as a licence tester, create the `premium` product: base plan `monthly` ₹119 auto-renewing, base plan `annual` ₹1,199 auto-renewing, offer `trial7` on both with a 7-day free phase and eligibility "New customer acquisition: never had this subscription". Expected: buying shows "Test card, always approves", `/api/me` turns premium, a hi-res stream is served. Cancel in Play → RTDN arrives → still premium until `expires_at`.

- [ ] **Step 3: Commit**

```bash
git add android && git commit -m "feat(android): play billing and paywall"
```

### Task 4.4: Deploy service and wire RTDN

**Files:**
- Create: `docs/deploy.md`

- [ ] **Step 1: Secrets and deploy**

```bash
gcloud secrets create MONGODB_URI --data-file=- 
gcloud run deploy hires-service --source service --region asia-south1 --allow-unauthenticated --min-instances 0 --max-instances 2 --memory 512Mi --set-env-vars MONGODB_DBNAME=hires,PLAY_PACKAGE_NAME=com.anish1050.hires,GOOGLE_CLIENT_ID=<web client id> --set-secrets MONGODB_URI=MONGODB_URI:latest,JWT_SECRET=JWT_SECRET:latest,R2_ACCOUNT_ID=R2_ACCOUNT_ID:latest,R2_ACCESS_KEY_ID=R2_ACCESS_KEY_ID:latest,R2_SECRET_ACCESS_KEY=R2_SECRET_ACCESS_KEY:latest,R2_BUCKET=R2_BUCKET:latest,PLAY_SERVICE_ACCOUNT_JSON=PLAY_SERVICE_ACCOUNT_JSON:latest,RTDN_SECRET=RTDN_SECRET:latest,CRON_SECRET=CRON_SECRET:latest
```

`--max-instances 2` caps spend if something loops. Confirm on the Cloud Run pricing page that the free tier applies to `asia-south1`; otherwise use `us-central1`.

- [ ] **Step 2: RTDN**

Create Pub/Sub topic `play-rtdn`; grant `google-play-developer-notifications@system.gserviceaccount.com` the Publisher role on it; create a push subscription to `https://<run-url>/api/billing/google/rtdn?token=<RTDN_SECRET>`; set the topic in Play Console → Monetization setup; click "Send test notification". Expected: a `billing_events` row with `test`.

- [ ] **Step 3: Play API access**

Create a service account `play-billing`, download its key into Secret Manager `PLAY_SERVICE_ACCOUNT_JSON`, invite its email in Play Console → Users and permissions with "View financial data" and "Manage orders and subscriptions".

- [ ] **Step 4: Commit the runbook**

```bash
git add docs/deploy.md && git commit -m "docs: cloud run deploy and rtdn runbook"
```

---

## Phase 5: Plays and Sound Intelligence stats

### Task 5.1: Play tracking

**Files:**
- Create: `.../playback/PlayTracker.kt`, `.../data/repo/PlayEventRepository.kt`, `app/src/test/.../PlayTrackerTest.kt`, `service/src/routes/plays.ts`, `service/tests/plays.test.ts`
- Modify: `HiResDatabase.kt` (table `pending_plays`), `PlaybackService.kt`, `service/src/app.ts`

**Interfaces:**
- Produces: `PlayTracker.onPlayingChanged(isPlaying, nowMs)`, `onItemChanged(trackId, nowMs)`, `flush(nowMs): PlayEvent?`; `PlayEvent(clientEventId, trackId, msPlayed, startedAt, quality, offline)`; `POST /api/plays`.

- [ ] **Step 1: Tracker test**

```kotlin
class PlayTrackerTest {
    @Test fun countsOnlyPlayingTime() {
        val t = PlayTracker(idFactory = { "e1" })
        t.onItemChanged("t1", 0); t.onPlayingChanged(true, 0)
        t.onPlayingChanged(false, 10_000); t.onPlayingChanged(true, 20_000)
        val e = t.onItemChanged("t2", 25_000)
        assertThat(e!!.msPlayed).isEqualTo(15_000)
        assertThat(e.trackId).isEqualTo("t1")
    }
    @Test fun dropsUnderThreeSeconds() {
        val t = PlayTracker(idFactory = { "e1" })
        t.onItemChanged("t1", 0); t.onPlayingChanged(true, 0)
        assertThat(t.onItemChanged("t2", 2_000)).isNull()
    }
}
```

- [ ] **Step 2: Implement tracker and queue**

`PlayTracker` keeps `currentTrack`, `startedAt`, `accumulatedMs`, `playingSince`. `onItemChanged` closes the previous item and returns its event if `msPlayed >= 3000`. Events go into Room `pending_plays`; `PlayEventRepository.sync()` posts up to 200 at a time on app start, every 20 events, and on `ProcessLifecycleOwner` stop, deleting rows on 200.

- [ ] **Step 3: Server route test and implementation**

Test: posting the same batch twice stores each event once; `ms_played` above `duration_ms + 5000` → 400; unknown `track_id` events are skipped.

Implementation: body `z.object({ events: z.array(z.object({ client_event_id: z.string().uuid(), track_id: z.string(), ms_played: z.number().int().min(3000), started_at: z.coerce.date(), quality: z.enum([...]), offline: z.boolean() }).strict()).min(1).max(200) }).strict()`. Look up tracks with `$in`, enrich with `artist_id`, `genres`, `duration_ms`, the user's current `tier`; validate `started_at` within 30 days and not more than 5 minutes in the future; `insertMany(..., { ordered: false })` and ignore duplicate-key errors (code 11000 in `writeErrors`).

- [ ] **Step 4: Run, commit**

```bash
git add android service && git commit -m "feat: play event tracking and ingestion"
```

### Task 5.2: Stats endpoint and Sound Intelligence screen

**Files:**
- Create: `service/src/helpers/stats.ts`, `service/src/routes/stats.ts`, `service/tests/stats.test.ts`, `.../ui/stats/{StatsScreen,StatsViewModel,GenreGrid}.kt`, `app/src/test/.../PeakWindowTest.kt`

**Interfaces:**
- Produces: `GET /api/stats` per spec 8.3; Kotlin `peakWindow(hour: Int): String` (e.g. 22 → "9 PM–12 AM").

- [ ] **Step 1: Server test**

Seed 4 plays for one user across 2 artists and 2 genres at known IST hours; assert `total_ms`, `songs_played`, `peak_hour`, top artist order, genre totals; plays of another user are excluded; `period=7d` excludes an 8-day-old play.

- [ ] **Step 2: Aggregation**

```ts
export function statsPipeline(userId: string, since: Date) {
  return [
    { $match: { user_id: userId, started_at: { $gte: since } } },
    { $facet: {
      totals: [{ $group: { _id: null, total_ms: { $sum: '$ms_played' }, tracks: { $addToSet: '$track_id' } } },
               { $project: { _id: 0, total_ms: 1, songs_played: { $size: '$tracks' } } }],
      hours: [{ $group: { _id: { $hour: { date: '$started_at', timezone: 'Asia/Kolkata' } }, ms: { $sum: '$ms_played' } } },
              { $sort: { ms: -1 } }, { $limit: 1 }],
      artists: [{ $group: { _id: '$artist_id', ms: { $sum: '$ms_played' } } }, { $sort: { ms: -1 } }, { $limit: 10 },
                { $lookup: { from: 'catalog_artists', localField: '_id', foreignField: '_id', as: 'a' } },
                { $project: { _id: 0, id: '$_id', ms: 1, name: { $first: '$a.name' }, image_key: { $first: '$a.image_key' } } }],
      genres: [{ $group: { _id: { $ifNull: [{ $first: '$genres' }, 'Others'] }, ms: { $sum: '$ms_played' } } }, { $sort: { ms: -1 } }],
    } },
  ];
}
```

The route folds genres after the top 3 into "Others" and presigns artist images. Timezone is IST for phase 1 (`ponytail:` use the device timezone sent as a query param when launching outside India).

- [ ] **Step 3: Screen (left mockup phone)**

`SectionHeader("Sound", "Intelligence")` + search + avatar. Listening Time `GlassCard`: hours and minutes in 56 sp light with small "h"/"m" units, overlapping 40 dp artist avatars (−12 dp offset) with a "+N" bubble, divider, "Song Played 239" and "Peak Time 9 PM–12 AM". Genre Distribution `GlassCard`: `GenreGrid` on `Canvas` places one rounded pill (height 12 dp, width proportional to share) per genre per row, lavender / sand / mint / graphite, on a faint grid; legend chips below. Week / month segmented control.

`PeakWindowTest`: 22 → "9 PM–12 AM", 0 → "11 PM–2 AM", 13 → "12 PM–3 PM".

- [ ] **Step 4: Run, commit**

```bash
git add android service && git commit -m "feat: sound intelligence stats"
```

---

## Phase 6: Recommendations

### Task 6.1: Embeddings and content neighbours

**Files:**
- Create: `service/ingest/embed.py`, `service/ingest/requirements.txt`, `service/ingest/neighbors.ts`, `service/src/helpers/vector.ts`, `service/tests/vector.test.ts`

**Interfaces:**
- Produces: `cosine(a, b): number`, `meanVector(vectors, weights): number[] | null`, `topNeighbors(id, all, k): {track_id, score}[]`.

- [ ] **Step 1: Vector helper tests**

```ts
import { describe, it, expect } from 'vitest';
import { cosine, meanVector } from '../src/helpers/vector.ts';

describe('vector', () => {
  it('cosine of identical is 1', () => expect(cosine([1, 2, 3], [1, 2, 3])).toBeCloseTo(1));
  it('cosine of orthogonal is 0', () => expect(cosine([1, 0], [0, 1])).toBeCloseTo(0));
  it('weighted mean', () => expect(meanVector([[0, 2], [2, 0]], [1, 3])).toEqual([1.5, 0.5]));
  it('empty mean is null', () => expect(meanVector([], [])).toBeNull());
});
```

- [ ] **Step 2: Implement**

```ts
export function cosine(a: number[], b: number[]): number {
  let dot = 0, na = 0, nb = 0;
  for (let i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
  return na === 0 || nb === 0 ? 0 : dot / Math.sqrt(na * nb);
}

export function meanVector(vectors: number[][], weights: number[]): number[] | null {
  const total = weights.reduce((s, w) => s + w, 0);
  if (vectors.length === 0 || total === 0) return null;
  const out = new Array<number>(vectors[0].length).fill(0);
  vectors.forEach((v, i) => v.forEach((x, d) => { out[d] += (x * weights[i]) / total; }));
  return out;
}
```

- [ ] **Step 3: embed.py**

`requirements.txt`: `laion_clap==1.1.6`, `torch` (CPU), `librosa`, `pymongo`, `numpy`.

```python
import argparse, os, numpy as np, laion_clap
from pymongo import MongoClient

GENRES = ["lo-fi", "pop", "r&b", "hip-hop", "rock", "indie", "electronic", "jazz", "classical", "ambient", "folk", "metal", "bollywood", "devotional"]

def main():
    p = argparse.ArgumentParser(); p.add_argument("--dir", required=True); args = p.parse_args()
    db = MongoClient(os.environ["MONGODB_URI"])[os.environ["MONGODB_DBNAME"]]
    model = laion_clap.CLAP_Module(enable_fusion=False, amodel="HTSAT-base")
    model.load_ckpt(os.environ["CLAP_CKPT"])  # music_audioset_epoch_15_esc_90.14.pt
    text = model.get_text_embedding([f"{g} music" for g in GENRES])
    text = text / np.linalg.norm(text, axis=1, keepdims=True)
    for t in db.catalog_tracks.find({"embedding": {"$size": 0}}, {"_id": 1}):
        path = os.path.join(args.dir, t["_id"], "lossless.flac")
        audio = model.get_audio_embedding_from_filelist(x=[path])[0]
        audio = audio / np.linalg.norm(audio)
        top = [GENRES[i] for i in np.argsort(-(text @ audio))[:2]]
        db.catalog_tracks.update_one({"_id": t["_id"]}, {"$set": {"embedding": audio.astype(float).tolist(), "genres": top}})
        print("embedded", t["_id"], top)

if __name__ == "__main__":
    main()
```

The ingest CLI keeps a local copy of each `lossless.flac` under `drops/.work/<track_id>/` for this step. Artist-provided genres in `album.json` win over zero-shot ones; `embed.py` only sets `genres` when the track's list is empty (adjust the `$set`).

Atlas UI → Search → Create Vector Search index on `catalog_tracks`, name `track_embedding`:
```json
{ "fields": [{ "type": "vector", "path": "embedding", "numDimensions": 512, "similarity": "cosine" }] }
```

`neighbors.ts`: load all `{_id, embedding}`, compute top 50 by `cosine` for each, write `track_neighbors.content`. `ponytail:` O(n²), fine to 10k tracks; switch to `$vectorSearch` per track beyond.

- [ ] **Step 4: Run, commit**

Run: `npm test -- vector`; `python ingest/embed.py --dir drops/.work`; `npx tsx ingest/neighbors.ts`
Expected: tests pass; every track has 512 numbers and 2 genres.

```bash
git add service && git commit -m "feat(recs): clap embeddings and content neighbours"
```

### Task 6.2: Discover, Daily Mixes, AI Discovery preview

**Files:**
- Create: `service/src/helpers/recs.ts`, `service/src/routes/discover.ts`, `service/tests/recs.test.ts`
- Modify: `service/src/routes/home.ts`, `service/src/app.ts`

**Interfaces:**
- Consumes: `cosine`, `meanVector`, `track_neighbors`.
- Produces: `score(candidate, ctx): number`, `matchPercent(cos): number`, `dailyMixes(scored, seed): Mix[]`, `seededShuffle(items, seed)`.

- [ ] **Step 1: Tests**

```ts
describe('recs', () => {
  it('match percent maps cosine to 50..99', () => {
    expect(matchPercent(1)).toBe(99);
    expect(matchPercent(0)).toBe(50);
    expect(matchPercent(-1)).toBe(50);
    expect(matchPercent(0.92)).toBe(96);
  });
  it('recently heard tracks sink', () => {
    const base = { cosine: 0.9, coplay: 0.5, freshness: 1 };
    expect(score({ ...base, heardRecently: true })).toBeLessThan(score({ ...base, heardRecently: false }));
  });
  it('seeded shuffle is stable per seed and differs across seeds', () => {
    const items = Array.from({ length: 20 }, (_, i) => i);
    expect(seededShuffle(items, 'u1:2026-11-01')).toEqual(seededShuffle(items, 'u1:2026-11-01'));
    expect(seededShuffle(items, 'u1:2026-11-01')).not.toEqual(seededShuffle(items, 'u1:2026-11-02'));
  });
});
```

- [ ] **Step 2: Implement**

```ts
export const matchPercent = (cos: number) => Math.min(99, Math.max(50, Math.round(50 + 50 * cos)));

export const score = (c: { cosine: number; coplay: number; freshness: number; heardRecently: boolean }) =>
  0.6 * c.cosine + 0.3 * c.coplay + 0.1 * c.freshness - (c.heardRecently ? 0.5 : 0);

export function seededShuffle<T>(items: T[], seed: string): T[] {
  let h = 2166136261;
  for (const ch of seed) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    h = Math.imul(h ^ (h >>> 15), 2246822507) >>> 0;
    const j = h % (i + 1);
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}
```

`matchPercent(0.92)` = round(96) = 96. Correct.

`discover.ts`:
1. Last 200 plays of the user joined to embeddings → `meanVector` (weights `ms_played / duration_ms`, liked ×2). None → cold start from `taste_artist_ids` tracks; none → editorial featured.
2. `$vectorSearch` `{ index: 'track_embedding', path: 'embedding', queryVector, numCandidates: 300, limit: 100 }` with `$project: { score: { $meta: 'vectorSearchScore' } }`. Atlas cosine score is `(1 + cos) / 2`; convert back: `cos = 2 * score - 1`.
3. Add co-play neighbours of the last 20 played tracks (`coplay` = their stored score, else 0). `freshness` = 1 if created in the last 30 days else 0. `heardRecently` = played in the last 14 days.
4. Sort by `score`, return top 24 with `match_percent`.

`home.ts`: `daily_mixes` = top 3 genres of the scored candidates, 30 tracks each, `seededShuffle` with `${userId}:${istDate}`, title "Daily Mix N", subtitle genre names; `discover_preview` = `{ count: 24, match_percent: round(mean of top 24), sparkline: top 16 match percents }`.

- [ ] **Step 3: Route test with in-memory Mongo**

`$vectorSearch` does not run on mongodb-memory-server. Put the Atlas call in `helpers/vector-search.ts` and `vi.mock` it in tests. Test: a user who played lo-fi tracks gets lo-fi first; returns 24 when the catalog has ≥ 24; recently played tracks appear after unheard ones of similar score.

- [ ] **Step 4: Run, commit**

```bash
git add service && git commit -m "feat(recs): discover, daily mixes, ai discovery preview"
```

### Task 6.3: Nightly co-play job

**Files:**
- Create: `service/src/routes/jobs.ts`, `service/tests/jobs.test.ts`

- [ ] **Step 1: Test**

Seed plays: users A and B played t1 and t2; user C played t1 and t3. Expect `track_neighbors[t1].coplay` contains t2 with score `2 / sqrt(3 · 2)` ≈ 0.816 and t3 with `1 / sqrt(3 · 1)` ≈ 0.577; plays under 30 s ignored; missing `Authorization: Bearer <CRON_SECRET>` → 401.

- [ ] **Step 2: Implement**

Aggregate the last 30 days of plays with `ms_played >= 30000` into `user → set(track)`. Count `users_per_track` and pair co-occurrence in memory (`ponytail:` O(users × tracks_per_user²) in memory, fine below ~10k users; move to an `$unwind`/`$group` pipeline or a batch job beyond). Write top 50 per track to `track_neighbors.coplay` with `bulkWrite` upserts.

- [ ] **Step 3: Schedule**

```bash
gcloud scheduler jobs create http coplay-nightly --location asia-south1 --schedule "30 21 * * *" --uri https://<run-url>/api/jobs/coplay --http-method POST --headers Authorization="Bearer <CRON_SECRET>"
```

21:30 UTC = 3:00 IST.

- [ ] **Step 4: Run, commit**

```bash
git add service docs && git commit -m "feat(recs): nightly co-play neighbours"
```

### Task 6.4: Discover screen

**Files:**
- Create: `.../ui/discover/{DiscoverScreen,DiscoverViewModel}.kt`

- [ ] **Step 1: Screen**

`SectionHeader("AI", "Discovery")`; top `AiDiscoveryCard` (same component as Library); list of 24 tracks with cover, title, artist and a match pill ("96 %") in `Mint`; "Play all" shuffles nothing (order is the ranking); three Daily Mix tiles in a row; a full-width "Recognize a song" button (mic icon) opening `Routes.RECOGNIZE`.

- [ ] **Step 2: Build, commit**

```bash
git add android && git commit -m "feat(android): discover tab"
```

---

## Phase 7: Song recognition

### Task 7.1: Fingerprint helper

**Files:**
- Create: `service/src/helpers/fingerprint.ts`, `service/tests/fingerprint.test.ts`

**Interfaces:**
- Produces: `landmarks(samples: Float32Array): Landmark[]` (`{ h: number; t: number }`), `bestMatch(query: Landmark[], stored: StoredLandmark[]): FingerprintMatch | null` (`{ tid, offsetMs, votes, confidence }`), `pcm16ToFloat(buf: Uint8Array): Float32Array`, constants `SAMPLE_RATE = 16000`, `HOP = 512`.

- [ ] **Step 1: Failing test with a synthetic melody**

```ts
import { describe, it, expect } from 'vitest';
import { landmarks, bestMatch, SAMPLE_RATE, HOP } from '../src/helpers/fingerprint.ts';

function rng(seed: number) {
  return () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 2 ** 32; };
}

function melody(seconds: number, seed: number): Float32Array {
  const rand = rng(seed);
  const out = new Float32Array(seconds * SAMPLE_RATE);
  const noteLen = SAMPLE_RATE / 5;
  for (let start = 0; start < out.length; start += noteLen) {
    const tones = [0, 1, 2].map(() => ({ f: 200 + rand() * 3500, a: 0.2 + rand() * 0.3 }));
    for (let i = 0; i < noteLen && start + i < out.length; i++) {
      const env = Math.exp(-i / (0.08 * SAMPLE_RATE));
      out[start + i] = tones.reduce((s, t) => s + t.a * env * Math.sin((2 * Math.PI * t.f * i) / SAMPLE_RATE), 0);
    }
  }
  return out;
}

function withNoise(x: Float32Array, level: number, seed: number) {
  const rand = rng(seed);
  return x.map(v => v + (rand() * 2 - 1) * level);
}

describe('fingerprint', () => {
  const song = melody(20, 1);
  const stored = landmarks(song).map(l => ({ ...l, tid: 7 }));
  const startFrame = 156;
  const excerpt = song.slice(startFrame * HOP, startFrame * HOP + 6 * SAMPLE_RATE);

  it('produces a sane landmark density', () => {
    const perSecond = stored.length / 20;
    expect(perSecond).toBeGreaterThan(10);
    expect(perSecond).toBeLessThan(400);
  });

  it('finds the track and offset in a noisy excerpt', () => {
    const match = bestMatch(landmarks(withNoise(excerpt, 0.05, 9)), stored);
    expect(match?.tid).toBe(7);
    expect(match?.offsetMs).toBe(startFrame * 32);
  });

  it('rejects an unrelated melody', () => {
    expect(bestMatch(landmarks(melody(6, 2)), stored)).toBeNull();
  });

  it('rejects pure noise', () => {
    expect(bestMatch(landmarks(withNoise(new Float32Array(6 * SAMPLE_RATE), 0.3, 3)), stored)).toBeNull();
  });
});
```

- [ ] **Step 2: Run to see it fail**

Run: `npm test -- fingerprint`
Expected: FAIL, module not found.

- [ ] **Step 3: Implement**

```ts
export const SAMPLE_RATE = 16000;
export const HOP = 512;
const FRAME = 1024;
const BINS = FRAME / 2;
const T_RADIUS = 10;
const F_RADIUS = 20;
const PEAK_MARGIN_DB = 10;
const FAN_OUT = 5;
const MAX_DT = 63;
const MIN_VOTES = 8;
const MIN_VOTE_RATIO = 0.03;
const FRAME_MS = (HOP / SAMPLE_RATE) * 1000;

export interface Landmark { h: number; t: number }
export interface StoredLandmark extends Landmark { tid: number }
export interface FingerprintMatch { tid: number; offsetMs: number; votes: number; confidence: number }
interface Peak { t: number; f: number }

export function pcm16ToFloat(buf: Uint8Array): Float32Array {
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const out = new Float32Array(buf.byteLength >> 1);
  for (let i = 0; i < out.length; i++) out[i] = view.getInt16(i * 2, true) / 32768;
  return out;
}

function fft(re: Float64Array, im: Float64Array): void {
  const n = re.length;
  for (let i = 1, j = 0; i < n; i++) {
    let bit = n >> 1;
    for (; j & bit; bit >>= 1) j ^= bit;
    j ^= bit;
    if (i < j) {
      [re[i], re[j]] = [re[j], re[i]];
      [im[i], im[j]] = [im[j], im[i]];
    }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const angle = (-2 * Math.PI) / len;
    const wr = Math.cos(angle);
    const wi = Math.sin(angle);
    for (let i = 0; i < n; i += len) {
      let cr = 1;
      let ci = 0;
      for (let k = 0; k < len / 2; k++) {
        const a = i + k;
        const b = a + len / 2;
        const tr = re[b] * cr - im[b] * ci;
        const ti = re[b] * ci + im[b] * cr;
        re[b] = re[a] - tr;
        im[b] = im[a] - ti;
        re[a] += tr;
        im[a] += ti;
        const next = cr * wr - ci * wi;
        ci = cr * wi + ci * wr;
        cr = next;
      }
    }
  }
}

function spectrogramDb(samples: Float32Array): Float32Array[] {
  const hann = Float64Array.from({ length: FRAME }, (_, i) => 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / (FRAME - 1)));
  const re = new Float64Array(FRAME);
  const im = new Float64Array(FRAME);
  const frames: Float32Array[] = [];
  for (let start = 0; start + FRAME <= samples.length; start += HOP) {
    for (let i = 0; i < FRAME; i++) { re[i] = samples[start + i] * hann[i]; im[i] = 0; }
    fft(re, im);
    const db = new Float32Array(BINS);
    for (let k = 0; k < BINS; k++) db[k] = 10 * Math.log10(re[k] * re[k] + im[k] * im[k] + 1e-12);
    frames.push(db);
  }
  return frames;
}

function slidingMax(row: Float32Array, radius: number): Float32Array {
  const out = new Float32Array(row.length);
  for (let i = 0; i < row.length; i++) {
    let m = -Infinity;
    for (let j = Math.max(0, i - radius); j <= Math.min(row.length - 1, i + radius); j++) m = Math.max(m, row[j]);
    out[i] = m;
  }
  return out;
}

function findPeaks(spec: Float32Array[]): Peak[] {
  if (spec.length === 0) return [];
  let sum = 0;
  for (const frame of spec) for (const v of frame) sum += v;
  const floor = sum / (spec.length * BINS) + PEAK_MARGIN_DB;
  const freqMax = spec.map(frame => slidingMax(frame, F_RADIUS));
  const peaks: Peak[] = [];
  for (let t = 0; t < spec.length; t++) {
    const lo = Math.max(0, t - T_RADIUS);
    const hi = Math.min(spec.length - 1, t + T_RADIUS);
    for (let f = 1; f < BINS; f++) {
      const v = spec[t][f];
      if (v < floor || v < freqMax[t][f]) continue;
      let isMax = true;
      for (let u = lo; u <= hi && isMax; u++) if (freqMax[u][f] > v) isMax = false;
      if (isMax) peaks.push({ t, f });
    }
  }
  return peaks;
}

function pairPeaks(peaks: Peak[]): Landmark[] {
  const out: Landmark[] = [];
  for (let i = 0; i < peaks.length; i++) {
    let paired = 0;
    for (let j = i + 1; j < peaks.length && paired < FAN_OUT; j++) {
      const dt = peaks[j].t - peaks[i].t;
      if (dt === 0) continue;
      if (dt > MAX_DT) break;
      out.push({ h: (peaks[i].f << 16) | (peaks[j].f << 6) | dt, t: peaks[i].t });
      paired++;
    }
  }
  return out;
}

export function landmarks(samples: Float32Array): Landmark[] {
  return pairPeaks(findPeaks(spectrogramDb(samples)));
}

export function bestMatch(query: Landmark[], stored: StoredLandmark[]): FingerprintMatch | null {
  const queryTimes = new Map<number, number[]>();
  for (const q of query) {
    const list = queryTimes.get(q.h);
    if (list) list.push(q.t); else queryTimes.set(q.h, [q.t]);
  }
  const votes = new Map<string, number>();
  let best: { key: string; count: number } = { key: '', count: 0 };
  for (const s of stored) {
    for (const qt of queryTimes.get(s.h) ?? []) {
      const key = `${s.tid}:${s.t - qt}`;
      const count = (votes.get(key) ?? 0) + 1;
      votes.set(key, count);
      if (count > best.count) best = { key, count };
    }
  }
  if (best.count < MIN_VOTES || best.count < MIN_VOTE_RATIO * query.length) return null;
  const [tid, offset] = best.key.split(':').map(Number);
  return { tid, offsetMs: offset * FRAME_MS, votes: best.count, confidence: best.count / query.length };
}
```

Peaks are scanned frame by frame and bin by bin, so `peaks` is already sorted by `t`, which `pairPeaks` relies on. `f < 512` so `f << 16` stays below 2³¹.

- [ ] **Step 4: Run, tune only if needed, commit**

Run: `npm test -- fingerprint`
Expected: 4 passed. If the density test fails, adjust `PEAK_MARGIN_DB` (higher = fewer peaks) and record the value in the commit message. Do not loosen `MIN_VOTES` to make the noise test pass.

```bash
git add service && git commit -m "feat(recognize): landmark fingerprinting"
```

### Task 7.2: Fingerprint ingest and recognize endpoint

**Files:**
- Create: `service/ingest/fingerprint-track.ts`, `service/src/routes/recognize.ts`, `service/tests/recognize.test.ts`
- Modify: `service/ingest/ingest-album.ts`, `service/src/app.ts`, `service/src/helpers/env.ts` (`AUDD_ENABLED`, `AUDD_MONTHLY_CAP`, `AUDD_API_TOKEN`, all optional)

- [ ] **Step 1: Ingest step**

`fingerprint-track.ts --track <id> --file <path>`: `ffmpeg -i <file> -ac 1 -ar 16000 -f f32le -` read into a `Float32Array`, `landmarks()`, delete old rows for that `tid`, `insertMany` in batches of 10,000 with `{ h, t, tid: fp_id }`. `ingest-album.ts` calls it after uploading.

- [ ] **Step 2: Route test**

Seed `fingerprints` from `melody(20, 1)` for a track with `fp_id: 7`; POST a 6-second PCM16 excerpt as `application/octet-stream`; expect `match.track.id` and `offset_ms`. Body over 320,000 bytes → 413. Unrelated melody with AudD disabled → `{ match: null, external: null }`.

- [ ] **Step 3: Implement**

```ts
recognize.post('/', rateLimit({ windowMs: 60_000, max: 10, key: c => c.get('userId') }), async c => {
  const body = new Uint8Array(await c.req.arrayBuffer());
  if (body.byteLength > 320_000) return fail(c, 413, 'too_large', 'Clip too long');
  if (body.byteLength < 64_000) return fail(c, 400, 'too_short', 'Clip too short');
  const query = landmarks(pcm16ToFloat(body));
  const hashes = [...new Set(query.map(q => q.h))];
  const stored = await MongoClientFind<StoredLandmarkDoc>('fingerprints', { h: { $in: hashes } }, { projection: { _id: 0, h: 1, t: 1, tid: 1 } });
  const match = bestMatch(query, stored);
  if (match) {
    const track = await MongoClientFindOne<TrackDoc>('catalog_tracks', { fp_id: match.tid });
    if (track) return ok(c, { match: { track: await trackViewById(track._id), offset_ms: match.offsetMs, confidence: match.confidence }, external: null });
  }
  return ok(c, { match: null, external: await externalRecognize(body) });
});
```

`externalRecognize` returns `null` unless `AUDD_ENABLED === 'true'` and the `counters` doc `audd:<yyyy-mm>` is below `AUDD_MONTHLY_CAP`; then it wraps the PCM in a WAV header, posts to `https://api.audd.io/` with `api_token`, increments the counter and returns `{ title, artist }` or `null`. The clip is never written anywhere.

- [ ] **Step 4: Run, commit**

```bash
git add service && git commit -m "feat(recognize): recognize endpoint with capped external fallback"
```

### Task 7.3: Recognize screen

**Files:**
- Create: `.../recognize/MicRecorder.kt`, `.../ui/recognize/{RecognizeScreen,RecognizeViewModel}.kt`

- [ ] **Step 1: Recorder**

```kotlin
class MicRecorder {
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(seconds: Int = 8): ByteArray = withContext(Dispatchers.IO) {
        val rate = 16_000
        val bytes = rate * 2 * seconds
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val source = MediaRecorder.AudioSource.UNPROCESSED
        val recorder = AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 8192))
            .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            ?: AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 8192))
        val out = ByteArray(bytes)
        try {
            recorder.startRecording()
            var read = 0
            while (read < bytes && isActive) {
                val n = recorder.read(out, read, bytes - read)
                if (n <= 0) break
                read += n
            }
        } finally {
            recorder.stop(); recorder.release()
        }
        out
    }
}
```

- [ ] **Step 2: Screen**

Permission request with rationale ("We listen for 8 seconds only when you tap, and never keep the recording"). A 160 dp pulsing lavender circle while recording, then "Matching…", then a result `GlassCard` (cover, title, artist, "at 1:12", Play from that position via `seekTo(offset_ms + 8000)`, Save) or "Not in our catalog yet" with a "Request it" button (`POST /api/library`-style call to a small `catalog_requests` insert added to `recognize.ts` as `POST /api/recognize/request`, body `{ title, artist }` strict).

- [ ] **Step 3: Manual check and commit**

Play an ingested track from a laptop speaker; tap Recognize on the phone. Expected: correct track within ±1 s.

```bash
git add android service && git commit -m "feat(android): recognize a song"
```

---

## Phase 8: Royalties, settings and release

### Task 8.1: Royalty report

**Files:**
- Create: `service/ingest/royalty-report.ts`, `service/src/helpers/royalty.ts`, `service/tests/royalty.test.ts`

**Interfaces:**
- Produces: `splitRoyalties(netRevenuePaise: bigint, holders: {id, payoutShare, premiumMs}[]): {id, payoutPaise: bigint}[]`.

- [ ] **Step 1: Test**

```ts
it('splits pro-rata by premium listening time and never overpays', () => {
  const rows = splitRoyalties(1_000_000n, [
    { id: 'a', payoutShare: 0.7, premiumMs: 3_000 },
    { id: 'b', payoutShare: 0.7, premiumMs: 1_000 },
  ]);
  expect(rows).toEqual([{ id: 'a', payoutPaise: 525_000n }, { id: 'b', payoutPaise: 175_000n }]);
});
it('zero listening pays nothing', () => {
  expect(splitRoyalties(1_000n, [{ id: 'a', payoutShare: 0.7, premiumMs: 0 }])).toEqual([{ id: 'a', payoutPaise: 0n }]);
});
```

- [ ] **Step 2: Implement**

```ts
export function splitRoyalties(net: bigint, holders: { id: string; payoutShare: number; premiumMs: number }[]) {
  const total = holders.reduce((s, h) => s + BigInt(h.premiumMs), 0n);
  return holders.map(h => ({
    id: h.id,
    payoutPaise: total === 0n ? 0n : (net * BigInt(Math.round(h.payoutShare * 10_000)) * BigInt(h.premiumMs)) / (10_000n * total),
  }));
}
```

Integer maths floors every share, so the sum never exceeds the pool.

`royalty-report.ts --month 2026-11 --net-revenue-paise 1234567`: aggregate `play_events` with `tier: 'premium'` in that IST month by track → `rights_holder_id`, run `splitRoyalties`, write `reports/royalties-2026-11.csv` (`rights_holder_id,name,email,premium_ms,share_percent,payout_paise`).

- [ ] **Step 3: Run, commit**

```bash
git add service && git commit -m "feat(ingest): monthly royalty report"
```

### Task 8.2: Settings and account deletion UI

**Files:**
- Create: `.../ui/settings/{SettingsScreen,SettingsViewModel}.kt`, `.../data/repo/PrefsRepository.kt`

- [ ] **Step 1: Implement**

DataStore-free: settings live in a plain `SharedPreferences` file (no secrets in it): Wi-Fi quality, mobile quality, download quality, hi-res over Bluetooth, loudness normalisation, USB DAC rate matching (shown only on API 34+). Account section: email, plan and renewal date, Manage subscription (opens `https://play.google.com/store/account/subscriptions?sku=premium&package=com.anish1050.hires`), Restore purchases, Sign out, Delete account (confirm dialog → `DELETE /api/me` → clear `AuthStore`, remove all downloads, clear Room → onboarding). Licences screen lists Media3, OkHttp, Coil, Manrope (OFL) and every CC BY track credit (artist, title, licence link) pulled from a `GET /api/credits` route added here (`catalog_tracks` where `licence` starts with `CC-BY`; add `licence` and `attribution` fields to `TrackDoc` and `album.json`).

- [ ] **Step 2: Commit**

```bash
git add android service && git commit -m "feat: settings, licences and account deletion"
```

### Task 8.3: Release on Play (closed test, then production)

**Files:**
- Create: `.github/workflows/release.yml`, `docs/play-listing.md`, `docs/privacy.md` (published via GitHub Pages)

- [ ] **Step 1: Signing**

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Keep `upload.jks` out of git; path and passwords in `local.properties` and as GitHub secrets. Enrol in Play App Signing.

- [ ] **Step 2: release.yml**

On tag `v*`: JDK 17, decode the keystore from a base64 secret, `gradle bundleRelease`, upload the AAB as a workflow artifact. Uploading to Play stays manual in phase 1 (no extra service account permissions needed).

- [ ] **Step 3: Listing and policies**

`docs/play-listing.md`: short and full description, screenshots of the three main screens, category Music & Audio, content rating questionnaire answers, Data Safety (email and name for account; app activity = plays for recommendations and royalties; audio recorded only on demand, processed ephemerally, not stored; purchase history via Google Play), account deletion URL, privacy policy URL, `RECORD_AUDIO` declaration, foreground service declarations (`mediaPlayback`, `dataSync`) with a video link of each.

- [ ] **Step 4: Closed test**

Upload to Closed testing, invite 12+ testers (friends, artists in the catalog), keep it running 14 days, fix what they find, then apply for production access.

- [ ] **Step 5: Commit**

```bash
git add .github docs && git commit -m "chore: release workflow and play listing"
```

---

## Self-review

**Spec coverage**

| Spec section | Tasks |
|---|---|
| 2 Catalog strategy, 17 quote request | 0.5, 1.4, 8.1 |
| 3 Pricing | 4.3 (Play products ₹119 monthly / ₹1,199 annual, 7-day free trial `trial7` for new users) |
| 4 Zero-investment infrastructure | 0.2, 0.5, 4.4 |
| 7.1–7.3 App stack and screens | 0.3, 2.1, 2.4, 5.2, 6.4, 7.3, 8.2, `docs/ui-preview.html` |
| 7.4 Hi-res path | 0.4, 2.2, 2.3 |
| 7.5 Downloads | 3.1, 3.2 |
| 7.6 / 11 Recognition | 7.1, 7.2, 7.3 |
| 8 Backend, collections, endpoints | 0.2, 1.1–1.3, 2.4, 4.1, 4.2, 5.1, 5.2, 6.2, 6.3, 7.2 |
| 8.4 Entitlement, 8.5 Quality gate | 4.1, 1.3 |
| 9 Ingest | 1.4, 6.1, 7.2, 8.1 |
| 10 Recommendations | 6.1–6.4 |
| 12 Billing | 4.1–4.4 |
| 13 Stats | 5.1, 5.2 |
| 14 Security and compliance | 0.3 manifest, 1.1 deletion, 3.1, 4.2, 4.4 secrets, 8.2, 8.3 |
| 15 Testing | tests in every task; device checks in 0.4, 2.3, 3.2, 7.3 |

**Known checks the executor must make against pinned library versions** (named, not hand-waved): the float-output setter name in Media3 (Task 0.4 proves it on a device), the `billing-ktx` 8.x query result shape (Task 4.3), and the LAION-CLAP weights licence (spec assumption 7).

**Type consistency:** `Quality` strings are identical in `types/catalog.ts`, `QualityPolicy.Quality.wire` and `shared/qualities.json`. `Tier` is `'free' | 'premium'` on the server and `Tier.FREE/PREMIUM` in Kotlin, mapped in `Dto.kt`. `TrackUris.cacheKey` is used by both the download request (3.2) and offline playback (2.2). `fp_id` is assigned in 1.4 and used as `tid` in 7.1–7.2.
