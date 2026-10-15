# Deploy runbook

Everything below stays inside free tiers. The only payment is the one-time USD 25 Google Play fee.
Set a budget alert of ₹1 on the Google Cloud billing account and a notification on Cloudflare before step 1.

## 1. Accounts (once)

| Service | What to create |
|---|---|
| Google Cloud | Project `hires-prod`; enable Cloud Run, Artifact Registry, Cloud Build, Pub/Sub, Cloud Scheduler, Secret Manager, Google Play Android Developer API |
| MongoDB Atlas | Free M0 cluster in AWS Mumbai; database `hires`; user `hires-service`; network access `0.0.0.0/0` (Cloud Run has no fixed IP on the free tier) |
| Cloudflare R2 | Bucket `hires-audio` (location hint APAC); API token scoped to that bucket with read/write |
| Google Play Console | Developer account (USD 25), app `com.anish1050.hires` |
| Google sign-in | OAuth client of type **Web** (its id is `GOOGLE_CLIENT_ID` and the app's `GOOGLE_WEB_CLIENT_ID`) and one of type **Android** with the SHA-1 of your debug and upload keys |

## 2. Secrets

```bash
printf '%s' 'mongodb+srv://...' | gcloud secrets create MONGODB_URI --data-file=-
openssl rand -base64 48 | tr -d '\n' | gcloud secrets create JWT_SECRET --data-file=-
openssl rand -hex 24 | tr -d '\n' | gcloud secrets create RTDN_SECRET --data-file=-
openssl rand -hex 24 | tr -d '\n' | gcloud secrets create CRON_SECRET --data-file=-
printf '%s' '<account id>' | gcloud secrets create R2_ACCOUNT_ID --data-file=-
printf '%s' '<key id>' | gcloud secrets create R2_ACCESS_KEY_ID --data-file=-
printf '%s' '<secret>' | gcloud secrets create R2_SECRET_ACCESS_KEY --data-file=-
gcloud secrets create PLAY_SERVICE_ACCOUNT_JSON --data-file=play-billing.json
```

Secret Manager is free for 6 active secret versions; beyond that it costs a few cents a month. To stay at zero,
put `R2_ACCOUNT_ID` and `R2_ACCESS_KEY_ID` in `--set-env-vars` instead (they identify, they do not authorise).

## 3. Deploy the API

```bash
gcloud run deploy hires-service --source service --region asia-south1 --allow-unauthenticated \
  --min-instances 0 --max-instances 2 --memory 512Mi \
  --set-env-vars MONGODB_DBNAME=hires,R2_BUCKET=hires-audio,PLAY_PACKAGE_NAME=com.anish1050.hires,GOOGLE_CLIENT_ID=<web client id>,AUDD_ENABLED=false \
  --set-secrets MONGODB_URI=MONGODB_URI:latest,JWT_SECRET=JWT_SECRET:latest,R2_ACCOUNT_ID=R2_ACCOUNT_ID:latest,R2_ACCESS_KEY_ID=R2_ACCESS_KEY_ID:latest,R2_SECRET_ACCESS_KEY=R2_SECRET_ACCESS_KEY:latest,PLAY_SERVICE_ACCOUNT_JSON=PLAY_SERVICE_ACCOUNT_JSON:latest,RTDN_SECRET=RTDN_SECRET:latest,CRON_SECRET=CRON_SECRET:latest
curl https://<run-url>/api/health
```

`--max-instances 2` caps cost if anything loops. Check on the Cloud Run pricing page that the free tier
applies to `asia-south1`; if it does not, use `us-central1` (audio still comes from R2's edge, so only API
calls get slower). Put the Cloud Run URL in `android/app/build.gradle.kts` `API_BASE_URL` for release.

## 4. Play billing

1. Play Console → Monetize → Subscriptions → create `premium` with base plans `monthly` (₹119, auto-renewing)
   and `annual` (₹1,199, auto-renewing). On each, add offer `trial7`: one free phase of 7 days, eligibility
   "New customer acquisition: never had this subscription".
2. Google Cloud: service account `play-billing`, JSON key into `PLAY_SERVICE_ACCOUNT_JSON`. Play Console → Users and
   permissions → invite its e-mail with "View financial data" and "Manage orders and subscriptions".
3. Pub/Sub topic `play-rtdn`; grant `google-play-developer-notifications@system.gserviceaccount.com` the Publisher
   role on it; push subscription to `https://<run-url>/api/billing/google/rtdn?token=<RTDN_SECRET>`.
4. Play Console → Monetization setup → topic `projects/hires-prod/topics/play-rtdn` → "Send test notification".
   Expect a `billing_events` row with no purchase token.

## 5. Nightly recommendations job

```bash
gcloud scheduler jobs create http coplay-nightly --location asia-south1 --schedule "30 21 * * *" \
  --uri https://<run-url>/api/jobs/coplay --http-method POST --headers "Authorization=Bearer <CRON_SECRET>"
```

21:30 UTC is 03:00 in India.

## 6. Catalog

Follow `service/ingest/README.md`: indexes, ingest an album, embeddings, neighbours, Atlas vector index,
then `npm run ingest:featured -- --tracks ...`.

## 7. Monthly royalties

1. Play Console → Download reports → Financial → earnings for the month; net revenue in paise.
2. `npm run royalties -- --month 2026-11 --net-revenue-paise <n>` → `reports/royalties-2026-11.csv`.
3. Pay each rights holder by UPI or bank transfer from money already received.
