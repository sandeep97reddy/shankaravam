# shankaravam-gateway (Phase 2)

Private R2 audio/receipt sharing behind Firebase-seat auth. Phones hold zero
secrets; the Sarvam key lives only as the `SARVAM_API_KEY` Worker secret.

## Files

- `src/lib.ts` — pure logic (speaker parity, validation, hashing). Vitest-covered.
- `src/index.ts` — fetch handler: JWKS verify → Firestore seat read → routes.
- `wrangler.toml` — `shankaravam-gateway`, `AUDIO_BUCKET` binding, public Firebase vars.
- `verify.mjs` — zero-cost curl matrix (see header for the ID_TOKEN recipe).

## Your remaining dashboard steps (code is done; ~10 min)

1. Worker → `shankaravam-gateway` → Settings → Variables:
   - Secret `SARVAM_API_KEY` — done already, do not touch.
   - Plain vars (public client identifiers, already shipped in the APK):
     - `FIREBASE_PROJECT_ID` = `shankaravam-bcec6`
     - `FIREBASE_WEB_API_KEY` = `AIzaSyAG2PSPjw4P2LO2nzNFkejH9ZDhvsZF2vM`
   - (Deploying via `wrangler deploy` below sets these automatically from
     `wrangler.toml` — dashboard entry is only needed if you never deploy.)
2. (Optional hardening) KV for the daily synthesis cap backend:
   `npx wrangler kv:namespace create RATE_KV`, then uncomment the block in
   `wrangler.toml` and redeploy. Without it the 1,000/day cap is per-isolate
   memory (best-effort, warns in logs).

## Deploy

```bat
cd tools\worker
npm install
npm test            REM 15 vitest green
npm run typecheck   REM tsc clean
npx wrangler login  REM one-time browser OAuth (your Cloudflare account)
npx wrangler deploy
```

## Verify

```bat
set WORKER_URL=https://shankaravam-gateway.<your-subdomain>.workers.dev
node verify.mjs                 REM zero-cost: 401/404 matrix
node verify.mjs                 REM with ID_TOKEN set: + 400/403/413 cases
node verify.mjs --live          REM spends ~1 Sarvam call (see script notes)
```

Paste the run output into `SESSION_HANDOFF.md` per the maintenance contract.

## R2 lifecycle (audio 30d, receipts retained)

`audio/{hash}.mp3` objects are immutable derived artifacts — safe to expire.
`receipts/*` are audit evidence — never expire them (no rule below touches
that prefix, and ledger rows live in Firestore, never in this bucket).

`r2-lifecycle.json` holds the single rule (`audio/` → expire after 30 days).
Apply once via the S3-compatible API (needs R2 S3 credentials from the
Cloudflare dashboard → R2 → Manage R2 API Tokens):

```bat
set R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
aws s3api put-bucket-lifecycle-configuration --bucket shankaravam-media --endpoint-url %R2_ENDPOINT% --lifecycle-configuration file://r2-lifecycle.json
aws s3api get-bucket-lifecycle-configuration --bucket shankaravam-media --endpoint-url %R2_ENDPOINT%   REM verify: 1 rule, audio/ → 30d
```

Wrangler does not manage R2 lifecycle rules, so this stays a one-time
operator step (re-run only if the bucket is recreated).

## Deliberate v1 simplifications (vs the Phase-2 spec)

- Audio/receipt GETs are auth-gated on every call instead of short-lived
  signed URLs: same protection (no world-readable bytes), zero clock-skew bugs.
- Receipt GET streams bytes directly instead of 302.
- Same-hash write races converge by re-HEAD before PUT (identical bytes, so a
  lost race only ever wastes one synthesis, never corrupts).
- `?eventId=` on audio GET carries an opaque UUID only — never donor PII
  (canonical text travels POST-only, and only its hash is logged).
