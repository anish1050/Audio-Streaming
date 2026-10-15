# Google Play listing and policy answers

## Release steps

1. Create the upload key (keep it and its passwords out of git and backed up):
   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
2. In `android/local.properties` add `upload.storeFile`, `upload.storePassword`, `upload.keyPassword`.
   For CI add secrets `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_PASSWORD`; tag `v0.1.0` to build.
3. Enrol in Play App Signing, upload the AAB to **Closed testing**, invite at least 12 testers, keep it open
   14 days (required for new personal developer accounts), then apply for production.

## Store listing

- **Category:** Music & Audio
- **Short description:** Hi-res lossless music up to 24-bit/192 kHz, offline downloads and song recognition.
- **Full description:** Stream independent artists in studio quality: FLAC up to 24-bit / 192 kHz, played with
  32-bit float output so nothing is cut to CD quality. Download for offline listening, recognise songs around you,
  see your listening stats, and get Daily Mixes picked from how your music actually sounds. Try Premium free for a week.
- **Screenshots:** Home (Featured), Stats (Sound Intelligence), Library, Now Playing with the signal-path badge.

## Data safety

| Data | Collected | Why | Shared |
|---|---|---|---|
| Name, e-mail (Google account) | Yes | Account | No |
| App activity: what you play and save | Yes | Recommendations, stats, paying artists | No |
| Purchase history | Yes, via Google Play | Subscription | No |
| Audio (microphone) | Only when you tap Recognize; processed and discarded, never stored | Song recognition | No |
| Device or other IDs | No | | |

Data is encrypted in transit. Users can delete their account and data in Settings or at the deletion URL.

## Declarations

- **Account deletion URL:** the GitHub Pages copy of `docs/privacy.md#delete-your-account`.
- **Microphone (`RECORD_AUDIO`):** used only on the Recognize screen after a tap, for 8 seconds.
- **Foreground services:** `mediaPlayback` for music playback; `dataSync` for user-started downloads.
  Record a short video of each for the declaration form.
- **Content rating:** music app with user-selected content; answer "no" to user-generated content and chat.
