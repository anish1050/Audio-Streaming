# Hi-Res Streaming

Android-first hi-res music streaming: FLAC up to 24/192, encrypted offline downloads,
song recognition, recommendations and listening stats, sold as a Google Play subscription.

| Folder | What |
|---|---|
| `android/` | Kotlin + Jetpack Compose app on Media3 |
| `service/` | Hono + MongoDB API for Cloud Run, plus the `ingest/` CLI that prepares tracks |
| `shared/` | Values both sides must agree on |
| `docs/` | Design spec, implementation plan, UI preview |

## Run

```bash
cd service && npm install && cp .env.example .env && npm run dev
```

```bash
cd android && gradle assembleDebug
```

Spec: `docs/superpowers/specs/2026-10-15-hires-streaming-design.md`
Plan: `docs/superpowers/plans/2026-10-15-hires-phase1.md`
Deploy: `docs/deploy.md`
