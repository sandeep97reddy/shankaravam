# Object-Storage Bucket Review — Backblaze B2 vs Alternatives, Audio Sharing & Cache Invalidation

> Status: engineering review of the other-agent "Cloudflare R2 Integration" proposal, cross-verified against the current codebase (Sept 2026).
> Verdict first: **Backblaze B2 is the best no-card bucket for this app, but a bucket alone does NOT solve the Sarvam-key or orphaned-audio problems. Do not wire the app directly to B2. Fix the local invalidation bugs first, then add sharing behind a tiny no-card signer/gateway.**
> This file is a review only — no code was changed. `AGENTS.md` Rule #2 ("zero audio in cloud storage") still stands until a bucket decision is implemented.

---

## 0. TL;DR for the Head

| Question | Answer |
|---|---|
| Is Backblaze B2 good without a card? | **Yes — best no-card S3 option.** Email-only signup, 10 GB free storage, S3-compatible, scoped app keys. Private buckets work with zero card. |
| Is it problem-free? | **No.** 3 traps: (1) **first public bucket requires a card/small payment** — stay private; (2) **free egress is 3× your stored bytes/month, not 10 GB/day** — for 45 MB stored that is ~135 MB/mo free, then $0.01/GB; (3) **keys on phones = same leak you have today with the Sarvam key** — B2 secret in the APK can be decompiled and the bucket wiped. |
| Should we do direct phone→B2 (like the R2 Approach A)? | **No.** Repeats the shared-secret mistake, adds 12–18 MB S3 SDK to the APK, keeps the redundant-synthesis race, and burns `ListObjects` ops. The other-agent report is right to reject this. |
| Should we do the Cloudflare Worker+R2 gateway (Approach B)? | Architecturally ideal, **operationally blocked**: R2 requires a card on file even at $0 usage. If you refuse Cloudflare cards, you cannot use R2 or Workers+R2. |
| What works with no card? | **B2 private bucket + a no-card signer**: (a) Supabase Edge Function or (b) a tiny Fly.io/Render free micro-service that holds the B2 + Sarvam secrets and mints short-lived presigned URLs / JIT-synthesizes. Phones hold zero secrets. Or (c) skip B2: **Supabase Storage (1 GB, no card)** covers receipts + audio for years at festival scale, with Row-Level Security instead of shared secrets. |
| Biggest finding from code audit? | The stale-audio bug in the other report is **real but understated**: grace-window correction leaves the old MP3 on disk (confirmed), **and** post-grace corrections never touch `donation.amount` at all, so even the *text* the queue speaks is stale. Plus `updateAudioStatus` bumps `updatedAt`, `readAudioMeta` does N+1 Firestore reads, and the Gemini provider table is stale (Firebase Storage left Spark in Feb 2026). Details in §3–§5. |
| Recommended order | **Phase 0 (no bucket, this sprint):** fix invalidation locally. **Phase 1 (bucket, next):** receipts only via Supabase Storage or B2-private+signer. **Phase 2 (later, optional):** CAS audio sharing. Do not start Phase 2 before Phase 0. |

---

## 1. Backblaze B2 — deep evaluation (Sept 2026, verified via Backblaze docs)

### 1.1 What B2 actually gives you

- **Signup:** email + verify, enable B2 in My Settings. **No card for private buckets.** Confirmed in B2 quickstart / bucket docs.
- **Free allowance:** first **10 GB storage always free**. Pay-go after that ~$6.95/TB/mo (~$0.006–0.007/GB).
- **Egress:** free up to **3× average monthly stored bytes**, then **$0.01/GB**. Unlimited free egress **only via Bandwidth Alliance partners** (Cloudflare, Fastly, etc.). Without a CDN in front, egress is metered.
- **API calls:** as of May 2026 Backblaze made **Class A/B/C free** (standard usage rules apply); Class D charged beyond ~2,500/day. The old "1M Class A / 10M Class B" R2-style caps do not apply to B2 — do not copy R2 math onto B2.
- **S3 compatibility:** real S3 endpoints per region (`s3.us-west-00X.backblazeb2.com`), SigV4, works with AWS SDKs / presigned URLs. Caveats: **only `private` and `public-read` at bucket level, no per-object ACL overrides**; master app key does **not** work with S3 — you must create a scoped app key; endpoint must match the account region.
- **Scoped app keys (the good part):** per-bucket, `readOnly / writeOnly / readAndWrite`, file-prefix restriction, expiry <1000 days. This is strictly better than one global secret — but a key shipped in the APK is still extractable.

### 1.2 Capacity math, corrected (the Gemini table is wrong here)

Assumptions from this codebase: Sarvam MP3 20–30 KB, WebP receipt ≤100 KB (`ReceiptCompressor.kt:20` `MAX_BYTES`), 1,000 donations + 200 receipts ≈ **45 MB stored**.

| Item | Gemini claim | Reality |
|---|---|---|
| B2 free egress | "3× stored/day (10 GB free egress/day)" | **Wrong.** 3× *average monthly storage per month*. For 45 MB stored → **~135 MB/mo free**, then $0.01/GB. 10 volunteers × 2,000 clips × 25 KB ≈ 500 MB/mo egress → ~365 MB over free → **~$0.004/mo**. Negligible, but not "10 GB/day free". |
| R2 Class A/B headroom | 500× headroom | Directionally right for R2, irrelevant for B2 (B2 calls are free under fair use). Do not budget R2 op caps for a B2 design. |
| Firebase Storage free | "5 GB, 1 GB/day, no card (Spark)" | **Stale.** Firebase removed Cloud Storage from Spark in **Feb 2026** — Blaze (card) now required; Spark `appspot.com` buckets return 402/403. Any plan that relies on free Firebase Storage is broken today. |
| Supabase free | "1 GB + 5 GB/mo, no card" | Roughly right (1 GB storage, ~2 GB bandwidth on current free), **but projects pause after ~1 week inactive** — bad for a seasonal festival app that sits idle 10 months. Factor resume latency + storage retention into the choice. |
| Appwrite free | "2 GB + 5 GB/mo, no card" | Roughly right for Cloud free, same pause/inactivity caveats, 1-bucket limits on some tiers. Fine for receipts, tight if you also push audio. |

Bottom line: **all four options hold years of festival data** (tens of MB/yr). Storage size never decides this — **auth model, card requirement, pause behavior, and who holds secrets** decide it.

### 1.3 The three B2 gotchas that bite this app

1. **Public-bucket card wall.** Backblaze docs: *"If this is your first time creating a public bucket: verify email + have payment history on file, or use the credit-card form to pay a small fee credited to your balance."* A no-card account **can create private buckets all day; the first public bucket is blocked**. Design for **private-only** + presigned URLs or a signer. A design that assumes `https://f000.backblazeb2.com/file/<public-bucket>/…` works card-free will fail at bucket-creation time.
2. **Egress/small-scale illusion.** At festival scale you will barely exceed free egress, but the "unlimited free" feeling only exists **behind Cloudflare/Fastly**. Direct `s3.*.backblazeb2.com` downloads from 10 phones are metered after 3× stored. Still pennies — just don't promise "infinite immunity from bill shock" without the CDN qualifier.
3. **No Firebase-style rules.** B2 has no `request.auth.uid == …` row rules. Anyone with a read-write app key can list/overwrite/delete the prefix it can see. Prefix-scoped, short-expiry, read-only vs write-only keys + a server-side signer are mandatory. Embedding one long-lived read-write key in the APK (Approach A) replaces the Sarvam-key leak with a **bucket-wipe leak**.

### 1.4 B2 vs the no-card field (opinion)

- **For S3 API compatibility:** B2 wins (true S3, presigned URLs, lifecycle rules). Supabase Storage is also S3-compatible these days but with its own RLS semantics; Appwrite is REST/SDK-first.
- **For zero-backend receipts with auth:** **Supabase Storage wins today** — no card, RLS tied to Auth, `AsyncImage`-friendly signed URLs, no APK key. B2 needs a signer you must host.
- **For pure cheapest bytes:** B2 wins ($0.006/GB, 10 GB free vs Supabase 1 GB / Appwrite 2 GB).
- **For seasonal idol-festival usage:** B2 wins on **no-pause** (your bucket doesn't sleep between Vinayaka Chavithi and Dasara); Supabase/Appwrite free projects pause.
- **Recommendation:** if you insist on B2 (reasonable), **pair it with a no-card signer from day one** — e.g. one Supabase project whose only job is Auth + Edge Function `mint-audio-url` / `mint-receipt-url` + holding the B2 app key and (later) the Sarvam key. Phones never see either secret. If you want zero extra backend, use Supabase Storage directly and skip B2.

---

## 2. Where the other-agent R2 report holds up, and where it doesn't

### 2.1 What the R2 report gets right (confirmed in code)

- **Shared-key dilemma — CONFIRMED.** `FirestoreSyncService.kt:752-780` (`readTtsKey`/`writeTtsKey` on `config/tts_settings`), `SessionPrefs.kt:184-187` + `SecureKeyStore.kt:50-82` (key mirrored to plain prefs), F5 auto-pull (`FirestoreSyncService.kt:270-296`). Every volunteer's phone holds the head's Sarvam key. Rules (`firestore.rules:154-157`) allow any signed-in device to read it; only the master admin can write it. Quota burn is per-device (`SessionPrefs.kt:222-235`, 20 calls/30 min).
- **Filename-coupled cache — CONFIRMED.** `SarvamTtsClient.kt:58-61,215-219` (`donation_{id}_{speaker}[_roster].mp3`), `DualTtsEngine.kt:89,199-203,237-253`. No content hash in the filename.
- **Stale playback on correction — CONFIRMED (grace path).** `DonationDetailViewModel.kt:38-68` grace edit does `donationRepository.save(copy(amount=newAmount, version+1))` with **no call to `deleteDonationCache`/`deleteDonationFiles`**. Next `playBest` hits `sarvam.cachedFile(id, speaker)` and plays the old amount. `deleteDonationCache` (`SarvamTtsClient.kt:170-171`) has **zero production callers** (only `DeleteLocalEventUseCase.kt:61` calls the static `deleteDonationFiles` for whole-event scrub, plus unit tests). The "dead code" claim is ~90% true — precise correction in §3.1.
- **Receipt isolation — CONFIRMED.** `FirestoreMappers.kt:73-88` (`expenseToMap` omits `receiptPath`), `FirestoreMappers.kt:90-112` (`expenseFromMap` forces `receiptPath=null`), `ExpenseEntity.kt:22` (local-only column). Other devices and the head can never see receipts. `ReceiptCompressor.kt` output stays on local disk.
- **Direct-client-to-bucket (Approach A) rejection — AGREED.** S3 SDK weight, shared-secret repeat, redundant-synthesis race, `ListObjects` burn. Correct call.
- **R2 free-tier shape & `r2.dev` rate-limit warning — DIRECTIONALLY CORRECT** for R2. Not re-verified dollar-for-dollar here because R2 is blocked by the card requirement anyway.

### 2.2 What the R2 report gets wrong or omits (architectural faults)

1. **`audioHash` in Firestore does not do what the report says.** The report claims Device A "stamps `audioHash`" and Device B "detects that `audioHash` is fresh, skips Sarvam, but has no file". Code reality (`FirestoreSyncService.kt:714-749`, `AnnouncementQueueViewModel.kt:745-772,806-823`): the three keys are `audioHash/audioGeneratedAt/audioGeneratedBy` written by **merge-only** `stampAudioMeta`, and the prefetch guard only **skips synthesis for 30 min** (`SessionPrefs.AUDIO_META_FRESH_MILLIS`) falling back to **native TTS** — it never downloads audio (there is no audio bucket), and `donationFromMap` (`FirestoreMappers.kt:42-69`) **ignores the three keys**, so Room never sees them. The user-visible symptom (B speaks robot voice) matches, but the mechanism is a **quota dedup hint, not content-addressed sharing**. A bucket design must not assume peers already compare hashes to decide downloads — that logic doesn't exist yet.
2. **Post-grace corrections are worse than "stale audio".** Outside the 5-min window `CorrectRecordUseCase.kt:38-51` appends a `Correction` row and **never touches the donation row**. The queue builds text from `donation.amount` (`AnnouncementTemplates.kt:120-133,140-173`), which ignores corrections. So the announcement is stale in **text**, not just in cached MP3. Any CAS design that hashes `donation.amount` without folding in the correction ledger will keep speaking the wrong amount forever. The report misses this entirely.
3. **Hash formula is under-specified.** Report: `SHA256(AnnounceText|speaker|voiceEngine).take(16)`. Code: `audioHashFor(text, language, speaker, roster)` = full SHA-256 hex of `"language|speaker|roster|text"` (`AudioImport.kt:11-16`). Problems with the report's version: (a) 16-hex-char truncation (64-bit) needlessly weakens collision resistance vs the existing full hash; (b) `voiceEngine` in the hash is redundant (native fallback is never cached); (c) `roster`/`language`/`eventName` must be explicit — roster text excludes `eventName`, full text includes it, and Telugu vs English vs Bilingual produce different bytes. Canonical text must come from **one builder** (`buildDonationAnnouncement`/`buildRosterItemAnnouncement`) with pinned inputs, or hashes diverge between devices.
4. **`GET /audio/{hash}?text=…&speaker=…` is not shippable.** Free-text-in-query leaks donor PII into edge logs, hits URL-length limits on long Telugu announcements, and lets anyone burn your Sarvam credits with arbitrary text. The gateway must accept **structured fields** (donorName, pronunciation, honorific, amount, item/qty/unit, language, roster, eventName, speaker) and rebuild text server-side with the same template, or accept **hash-only** after the client proves the hash. The report's JIT route needs this redesign.
5. **N+1 Firestore read amplification is unmentioned.** The freshness guard does **one `readAudioMeta` document read per missing donation** (up to 50/ pass, `AnnouncementQueueViewModel.kt:728-736,755-767`). At Spark's 50K reads/day, a 10-counter pandal with 200 queued rows can burn thousands of reads just deciding *not* to synthesize. A bucket design should replace per-row meta reads with the bucket's own `HEAD`/`GET` (or a single manifest), or the "savings" from sharing audio are eaten by meta reads.
6. **Receipts via public URL + Coil leaks PII.** Receipts show vendor names, amounts, phone numbers. Serving them from a public R2 custom domain with plain `AsyncImage` (as sketched) makes festival finances world-readable and un-revocable. Receipts must be **private + signed URLs with minutes-long expiry**, fetched with auth headers, with Firestore rules gating who may mint them.
7. **Lifecycle vs Rule #3 confusion.** The report's "delete `audio/` after 60 days" is fine (audio is a derived artifact), but it must state explicitly that **ledger rows, corrections, and receipts are never lifecycle-deleted** — otherwise it reads as violating the non-destructive-ledger invariant. B2 lifecycle rules are per-prefix, same as R2 — implement `audio/* → 60d`, `receipts/* → keep`.
8. **Worker auth hand-wave.** "Validate Firebase JWT" in a Worker is real work (fetch Google certs, verify RS256, check `exp`/`aud`, handle revoked/pending seats per `firestore.rules`). Without it, the JIT endpoint is an open Sarvam proxy. Budget this; don't ship the gateway without it.
9. **Double-synthesis race unsolved without a single writer.** In a pure client-upload (B2-direct) world, two offline phones that come online together both miss the bucket, both call Sarvam, both PUT the same hash. The Worker (single writer + `PUT`-if-absent) solves this; B2-direct does not. The report correctly prefers the gateway but doesn't call out that B2-direct *requires* conditional writes (`If-None-Match`) or a Firestore lock to avoid double quota burn.
10. **Rule #2 forbids the whole thing today.** `AGENTS.md` Rule #2 + `ARCHITECTURE.md` §2/§7/§8 ("zero TTS audio in cloud storage", "audio never here") make *any* audio bucket a rules violation until those files are amended with the new invariant (CAS, private-only, lifecycle, no keys on device). The blueprint omits the docs migration.

---

## 3. New bugs & issues found by this audit (all cross-verified)

### 3.1 P0 — correction never invalidates audio (grace path)

- **Path:** `DonationDetailViewModel.kt:38-68` → `CorrectRecordUseCase.kt:32-36` (`applyGraceEdit`) → `DonationRepositoryImpl.save` → `DonationDao.upsert` (REPLACE). No `deleteDonationCache`, no `updateAudioStatus(NOT_GENERATED)`, no version of the announcement text is compared.
- **Effect:** amount/pronunciation/honorific fix within 5 min keeps the old `donation_{id}_{speaker}[_roster].mp3`; queue plays the wrong amount in the crisp Sarvam voice (worse than robot voice — confidently wrong money on a horn speaker).
- **Also:** `DonationDao.updateStatus` (`DonationDao.kt:42-43`) bumps `version` (correct — status is ledger), but grace amount edits go through `upsert`, which is fine; the missing piece is purely cache invalidation.
- **Fix (no bucket):** in the grace-edit lambda, call `ttsEngine`/`sarvam.deleteDonationCache(id)` (all speakers + roster + imports? careful: human imports are intentional overrides — decide: grace amount edit should drop Sarvam clips but **keep** human imports? No — a human recording of the old amount is equally wrong; drop both and reset `audioStatus=NOT_GENERATED`). Same for pronunciation/honorific edits if any UI edits them (check donation edit screens — if only amount is editable, scope to amount).

### 3.2 P0 — post-grace corrections don't change what is spoken

- **Path:** `CorrectRecordUseCase.kt:38-51` (appends `Correction`, original untouched) + `AnnouncementTemplates.kt` (reads `donation.amount` only).
- **Effect:** after 5 min, the ledger is correct (original + correction row) but the horn speaks the original amount. Balance (`CalculateBalance`/`ObserveEventTotalsUseCase`) presumably folds corrections in; voice does not. This is a **text bug**, not a cache bug — CAS hashing alone won't fix it.
- **Fix:** define `effectiveAmount(donation, corrections)` (and effective item/qty?) in domain, use it in **both** balance and announcement builders, and include the correction id/version in the audio hash. Until then, any audio-sharing hash must include the correction state or it cements the wrong figure.

### 3.3 P1 — `updateAudioStatus` bumps `updatedAt` (masks remote edits)

- **Path:** `DonationDao.kt:49-50` (`SET audioStatus, updatedAt=now`), comment claims "bumps neither version nor syncStatus" — true, but it **does** bump `updatedAt`.
- **Effect:** local `updatedAt` drifts ahead of cloud on every prefetch (`AnnouncementQueueViewModel.kt:791-793`). Later, `isRemoteNewer` (`FirestoreMappers.kt:242-247`, requires `remoteUpdatedAt > localUpdatedAt && remoteVersion != localVersion`) is harder to satisfy — a peer's genuine edit with a slightly older stamp can be ignored, leaving a silent divergence (local keeps old money, no CONFLICT flag). `updateAudioStatus` also advances no watermark directly, but the inflated `updatedAt` is what the next `stampForPush` uploads, smearing audio timing into ledger timestamps.
- **Fix:** `UPDATE donations SET audioStatus=:status WHERE id=:id` (no `updatedAt`), or move audio state to a separate non-synced table. Same audit for any other local-only bookkeeping.

### 3.4 P1 — `readAudioMeta` N+1 burns Spark reads

- **Path:** `AnnouncementQueueViewModel.kt:755-772` per-missing-row `syncService.readAudioMeta` (`FirestoreSyncService.kt:722-734`, one `donations/{id}` get each).
- **Effect:** up to ~50 extra reads per prefetch pass per device, only when `cloudSyncEnabled`. At a busy counter this dwarfs the 20-call Sarvam quota it protects. Foreground listeners (`ForegroundSyncManager.kt`) already stream ledger deltas — piggyback audio freshness on the existing snapshot (or drop the guard once a bucket `HEAD` exists).
- **Fix:** remove the guard when a bucket exists (bucket `HEAD {hash}` is the freshness signal); until then, cap guard reads per pass (e.g. 5) and cache results in-memory per hash.

### 3.5 P1 — audio meta keys are writable by any collector (voice-suppression DoS)

- **Path:** `firestore.rules:78-82` (`donations` create/update by any `canWriteLedger`), `stampAudioMeta` merge (`FirestoreSyncService.kt:736-749`).
- **Effect:** any compromised/rogue collector can stamp fresh `audioHash` values, causing peers to skip synthesis for 30 min (native fallback). Low severity (voice degrades, money untouched; `donationFromMap` ignores the keys so Room is safe), but a bucket design that trusts `audioHash` for *downloads* must validate the hash against canonical text — otherwise a forged hash serves the wrong audio.
- **Fix:** gateway recomputes the hash from structured fields and ignores client-claimed hashes; keep client `stampAudioMeta` as a hint only.

### 3.6 P2 — `pruneCache` leaves phrase clips unbounded; import slot semantics fragile

- **Path:** `SarvamTtsClient.kt:191-207` filters `donation_*.mp3` only; `phrase_*.mp3` (`phraseCacheFileName`, `SarvamTtsClient.kt:225-229`) never pruned. `importRosterClips` writes `donation_{id}_roster.mp3` (`AnnouncementQueueViewModel.kt:631-643`) — the legacy/human slot, which the P1 migration (`migrateLegacyToSpeaker`, `SarvamTtsClient.kt:278-306`) treats specially (UUIDs have no underscores, so the shape is unambiguous — correct, but fragile if IDs ever change format).
- **Effect:** slow disk growth across festivals/presets/languages; a future CAS migration must handle three namespaces (Sarvam CAS, phrase CAS, human per-donation override) without collision.
- **Fix:** extend pruning to `phrase_*.mp3` with the same age/count ceiling (keep chime/test files exempt, as today).

### 3.7 P2 — receipts: no sharing, no auth, no retry

- Covered in §2.1 (mapper drops `receiptPath`). Additionally: no upload worker, no `receiptUrl` column, no Coil auth path, no WorkManager constraints (metered/charging/storage-not-low). A receipt bucket needs all four; the R2 sketch shows only the PUT.

---

## 4. What "good" looks like with a no-card constraint

### 4.1 Non-negotiables (from `AGENTS.md`, extended)

1. **Offline-first preserved:** save → Room <10 ms, UI updates, haptic. Audio fetch is **prefetch-only, non-blocking, 1.5 s timeout**, immediate native fallback. Playback path never awaits network.
2. **Zero secrets on phones (new):** no Sarvam key, no B2 secret in APK/prefs/Firestore-readable-to-volunteers. The head's Sarvam key moves to the signer/gateway env. `config/tts_settings` keeps only `{ defaultSpeaker, voiceVersion }`.
3. **Private-only buckets:** no public audio/receipt URLs. Signed URLs, minutes-long expiry, Firestore-gated minting.
4. **CAS for Sarvam audio, per-donation slot for human recordings:** `audio/{hash}.mp3` (shared, immutable, `Cache-Control: immutable`), `donation_{id}[_roster].mp3` stays the human-override slot and always wins playback. Phrases get their own `phrase/{hash}.mp3`.
5. **Corrections-aware hashing:** hash inputs = canonical announcement fields **including effective (post-correction) amount** + language + roster + speaker + template version. Template bump → new hash (automatic invalidation).
6. **No `ListObjects` from phones.** Direct `HEAD`/`GET {hash}` only. Receipt lists come from Firestore (`receiptUrl`), never bucket listing.
7. **Lifecycle:** `audio/*` → expire ~60d; `receipts/*` → retain (audit). Ledger/corrections never expire.

### 4.2 Recommended phasing (no-card)

- **Phase 0 — no bucket (do first).** Fix §3.1–§3.3 locally: invalidate Sarvam clips on grace edits, introduce `effectiveAmount` for voice, stop bumping `updatedAt` for audio state, cap `readAudioMeta`. Add `audioHashFor` coverage for correction-aware text. Verify: `testDebugUnitTest` + airplane-mode counter loop + correct-then-play manual test (₹500→₹5,000 must speak ₹5,000).
- **Phase 1 — receipts only (smallest bucket win).** One private bucket (`receipts/{eventId}/{expenseId}.webp`), `receiptUrl` in `expenseToMap`/`expenseFromMap` (plus Room migration for the column), `ReceiptUploadWorker` (WorkManager, backoff, metered-allowed), Coil fetch via signed URL. Supabase Storage **or** B2-private+signer both work; Supabase is less code if you accept pause behavior, B2+signer is better if you want no-pause + S3 semantics.
- **Phase 2 — audio sharing (only after Phase 0).** Migrate Sarvam cache filenames to CAS (`audio_{hash}.mp3` alongside legacy lookup during transition), gateway JIT (signer holds Sarvam key; R2 replaced by B2-private or Supabase). Keep human-override slot. Delete the Firestore Sarvam-key distribution (`readTtsKey`/`writeTtsKey` → speaker-only).

### 4.3 Minimal gateway contract (works for B2-private or Supabase)

```text
POST /v1/audio/resolve   { donationFields…, language, roster, speaker, templateVersion }
  → 200 { hash, url (5-min signed GET), cached: bool }   # signer recomputes canonical text+hash, HEADs bucket, JIT-synthesizes via Sarvam on miss, PUTs, returns signed URL
PUT  /v1/receipts/{eventId}/{expenseId}.webp   (auth header, content-length + sha256 checks, ≤150 KB)
GET  /v1/receipts/{eventId}/{expenseId}        → 302 to 5-min signed URL (never the raw bucket URL)
```

- Auth: Firebase ID token (phones already use Google Sign-In) validated server-side; seat check (`members/{uid}` active/collector) before minting. This closes the open-proxy hole in the R2 sketch.
- Client: OkHttp only (already in `libs.versions.toml`), no S3 SDK, no APK size regression. Download to `cacheDir/audio/audio_{hash}.mp3`, play via existing `playFile` path.

---

## 5. Cross-verification appendix (claim → code → verdict)

| # | Other-report / Gemini claim | Code | Verdict |
|---|---|---|---|
| 1 | Sarvam key in `/config/tts_settings` pulled to every phone | `FirestoreSyncService.kt:752-780`, `SessionPrefs.kt:184-187`, `SecureKeyStore.kt:50-82`, `firestore.rules:154-157` | **Confirmed.** Remove in Phase 2. |
| 2 | Device B sees fresh `audioHash`, skips Sarvam, has no file → robot voice | `FirestoreSyncService.kt:714-749`, `AnnouncementQueueViewModel.kt:755-772`, `FirestoreMappers.kt:42-69` (ignores meta) | **Symptom confirmed, mechanism overstated.** It's a 30-min quota hint, not a sharing protocol. No download exists. |
| 3 | Cache keyed by `donation.id`+speaker; correction plays stale audio | `SarvamTtsClient.kt:58-61,215-219`, `DualTtsEngine.kt:199-203`, `DonationDetailViewModel.kt:38-68` (no invalidation) | **Confirmed (P0).** |
| 4 | `deleteDonationFiles` dead code | `SarvamTtsClient.kt:170-171,259-267`, `DeleteLocalEventUseCase.kt:61` | **Partially true.** Static helper IS used for event scrub; instance `deleteDonationCache` has no production caller. |
| 5 | Receipts local-only | `FirestoreMappers.kt:73-112`, `ExpenseEntity.kt:22` | **Confirmed.** |
| 6 | B2 "10 GB free egress/day" | Backblaze docs (3× monthly avg; §1.2) | **Wrong.** ~135 MB/mo free at festival scale, then $0.01/GB. Still pennies. |
| 7 | Firebase Storage 5 GB free, no card | Firebase Feb 2026 Blaze requirement | **Stale.** Do not plan on free Firebase Storage. |
| 8 | `GET /audio/{hash}?text=…` JIT | — (no such endpoint) + PII/abuse analysis §2.2.4 | **Not shippable as sketched.** Use structured-fields POST. |
| 9 | 60-day audio lifecycle | — (no bucket yet) | **OK with Rule #3 caveat** (§2.2.7). |
| 10 | Worker validates Firebase JWT | — (no worker yet) | **Underestimated work.** Budget cert verification + seat checks. |

---

## 6. Suggested next step

If you want, start **Phase 0 only**: (1) grace-edit cache purge, (2) `effectiveAmount`-aware announcement text, (3) `updateAudioStatus` without `updatedAt`, (4) cap `readAudioMeta`. Each is a small, testable diff against the files cited above, verifiable with `.\gradlew.bat testDebugUnitTest` + `assembleDebug` and the airplane-mode counter loop in `ARCHITECTURE.md` §9. Open the bucket discussion only after the queue speaks corrected amounts correctly offline — otherwise the bucket will faithfully distribute the wrong audio faster.

*Alpha note (2026-09-14): Backblaze pricing/pause behavior changes quickly — re-check the B2 pricing + transaction pages and the Supabase/Appwrite free-tier pages before spending. The architectural conclusions (private-only, zero secrets on phones, CAS, corrections-aware hashing, no `ListObjects`) survive pricing changes.*
