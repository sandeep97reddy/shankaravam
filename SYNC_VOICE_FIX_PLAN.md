# Team Sync + Voice Fix Plan (2026-09-13)

> Status 13-09-2026: **Phases 1–6 DONE (ALL PHASES COMPLETE)** ✅ — network-default voice + offline retry fallback + UI badges + Sarvam phrase caching + queue wiring + unit tests. Verify: assembleDebug + 131/131 tests green.

Owner answers locked in. Build order: **F1 → F6, strictly in sequence.**

| # | Owner Q | Locked decision |
|---|---|---|
| 1 | Invite+Join into gear icon (ADMIN), no duplicate event | YES — single canonical Team section in `AdminSettingsScreen`, gated on Google sign-in, Join works with zero local events |
| 2 | QR camera vs gallery | Gallery picker + WhatsApp share NOW (zero permission, ~100 lines, uses existing `zxing:core`); ML Kit camera scan deferred to F2b follow-up |
| 3 | Sarvam sync model | Global-key auto-pull NOW (no rules redeploy, no client break); per-event keys deferred post-festival |
| 4 | Sync tempo | 5-min periodic + on-foreground + debounced post-save auto-sync + manual + full-resync. No snapshot listeners (battery + read-cost, unjustified at pandal scale) |
| 5 | TTS default + Sarvam split-voice | Default = best Android NETWORK te-IN voice (fallback embedded te-IN → system), only when user never picked; existing picks + Sarvam/imported caches untouched. Fix intro/outro to use Sarvam (cached `intro_*`/`outro_*` clips) so full queue speaks one voice |

---

## F0 — Why each bug happens (root causes, file-pinned)

1. **Duplicate event:** `CloudSyncScreen.kt:550` gates `JoinCard` on `event != null`. Zero-event installs must fabricate a dummy to reach Join. `requestToJoin` (`FirestoreSyncService.kt:269-346`) does pull the header, but `setCurrentEventId` only fires when Room holds the event (`:337`) — on deny/slow pull the dummy stays forever.
2. **QR:** only `QRCodeWriter().encode` exists (`CloudSyncScreen.kt:951`). No scanner. Payload `shankaravam://join/$code` has no parser — scanned text would fail `isValidShareCode`.
3. **Sync dead:** (a) watermark bug — `setLastSyncMillis(now)` (`:125`) + `whereGreaterThan("updatedAt", since)` misses offline rows with older stamps forever (matches "worked yesterday, dead today"); (b) `cloudSyncEnabled` defaults false and `requestToJoin` never enables it — collectors never background-sync; (c) single `runCatching` aborts whole sync on one DENIED row, generic toast, `SyncWorker` returns `retry()` even on auth errors (hot loop).
4. **Counter-IDs:** `rawCounterName()` (`SessionPrefs.kt:105`) returns `Counter-XXXX` when the collector never set a counter name (field buried in Admin accordion). Member docs store that fallback as primary; `TeamRow` prefers it over Google `displayName`.
5. **Sarvam not shared:** `/config/tts_settings` write = master admin only (`firestore.rules:156`). Non-admin publish fails → "saved on this device only". Nothing auto-pulls on sign-in/sync — every counter keeps its own key/speaker.
6. **Split voice:** `playSequence` (`AnnouncementQueueViewModel.kt:476-495,550-562`) speaks intro/outro via `engine.speakPhrase()` which is **native-only** (`DualTtsEngine.kt:263-270`). Prefetch only generates per-donation texts. So Sarvam mode = native intro → Sarvam name → native outro by construction.
7. **System-default voice:** `AndroidTtsClient.onInit` (`:44-100`) sets language te-IN but never selects a voice — engine uses OEM default (often robotic embedded). `getAvailableTeluguVoices()` returns names only (no network/quality flags for a picker).

---

## F1 — Team section moves into gear (ADMIN), sign-in gated, zero-event Join

**Goal:** collectors never create an event; head's Invite lives next to voice/counter settings behind login.

### Break-risk analysis (Q1 — nothing breaks if these 6 are handled)
1. `AdminSettingsScreen.kt:640-647` early-returns on `event == null` — Join moved verbatim would reproduce the same bug inside gear. **Must delete that gate for the Team section** (language/voice/counter/account cards already render event-less via `UiState` null-branch `:133-147`).
2. Dual state: `CloudSyncViewModel.codeTick` vs `AdminSettingsViewModel.keyTick` diverge if Invite lives in both. **Single canonical home: ADMIN.** `CLOUD_SYNC` route becomes a redirect to ADMIN (keep route constant for back-compat, render the same Team composable) or is removed with Dashboard tile repointed — pick redirect to avoid dead deep-links.
3. Dashboard `SecondaryUtilitiesRow` Sync tile + `NavGraph CLOUD_SYNC` + `onOpenAdmin` chain must be rewired together; missing one strands a button.
4. Approvals/Team need `eventId`. Gear must show current-event name + reuse `EventViewModel` switcher context (head with 2 festivals approves the wrong one otherwise).
5. `CloudSyncViewModel` tests reference `publishCode/join/approve/refreshMembers` — move logic to a shared `TeamSyncSection` VM or keep `CloudSyncViewModel` as the section VM hosted inside ADMIN (preferred: zero logic move, pure recomposition).
6. Rule #1: dashboard Donate/Expense/Announce stay fully offline; only the new Team accordion hides when signed-out (shows Account card + "sign in to join" explainer).

### Steps
- New `presentation/settings/TeamSyncSection.kt`: Account (reuse), Sync toggle + Sync-now + Full-resync + diagnostics (last sync/error/pending counts), Invite (event-scoped, head-gated Close), Join (global: code field + counter-name prompt + gallery-QR button + `parseJoinCode`), Approvals, Team roster (read-only for active collectors, manage for head).
- `AdminSettingsScreen`: remove `event == null` early-return for this section only; embed section (collapsed accordion, expanded on `?team=1` deep arg from dashboard Sync tile).
- `NavGraph`: `CLOUD_SYNC` → redirects to `ADMIN?team=1`. Dashboard Sync tile navigates there.
- `join()`: on success `setCurrentEventId(joined)`, auto-enable `cloudSyncEnabled` + `schedulePeriodic` + `syncNow`, offer dummy-cleanup when a zero-record local event exists.
- `parseJoinCode()`: accepts `shankaravam://join/XXXXXX`, raw code, full URL; unit-tested.

### Verify
- Fresh install, zero events, signed-in → gear shows Join, no "create first" block; join switches to head's event, no dummy.
- Signed-out → Team section hidden, donate loop unaffected.
- `assembleDebug + testDebugUnitTest` green.

---

## F2 — QR via gallery + WhatsApp share (no camera permission)

**Why gallery first:** zero new permission, zero Play-Services risk, ~100 lines, directly serves the stated flow (head screenshots QR → WhatsApp → collector picks from gallery). Camera (ML Kit + CameraX + `CAMERA` permission + preview lifecycle) is 3-4× the code and needs field-light testing; defer to F2b.
- Decode with existing `zxing:core` (`QRCodeReader` + `HybridBinarizer` + custom `LuminanceSource` from Bitmap pixels — no new dep).
- `ActivityResult.GetContent("image/*")` → decode → `parseJoinCode` → prefill Join field.
- Share button: render QR bitmap to `cacheDir/share/` + `FileProvider` → WhatsApp/text chooser (`ShareSheet` pattern).
- QR bitmap generation moves off main thread + disk/mem cache (fixes 262k `setPixel` jank on every recompose).

### Verify
- Screenshot QR → WhatsApp → gallery pick joins correctly; typed code still works; invalid image shows friendly error.

## F3 — Sync reliability (the "dead today" fix)

- Watermark: persist `max(remote.updatedAt seen)`, query `since - 5min fudge`; **Full resync** button (`since=0`, `limit(200)` pages).
- Per-row try/catch: DENIED row → `SYNC_FAILED` + reason, sync continues; UI shows last error verbatim (not "Sync failed. Will retry.").
- `join/publish` auto-enable sync + schedule; `SyncWorker`: auth/config/off → `success()`, network-only → `retry()`; add on-foreground expedited + 10 s debounced post-save auto-sync when enabled.
- Header writes `SetOptions.merge()`; 5-min periodic (was 15-min).
- **Free-tier math (Spark ≈ 50k reads / 20k writes / day):** 5 counters × 288 polls/day × 2 mostly-empty queries ≈ 3k reads + ~1k doc writes for 500 donations — ~10% of quota. Realtime listeners cost the same reads but hold radios/battery open; unjustified. 5-min + on-save debounce gives ≤10 s freshness after any local save, ≤5 min cross-device — matches the owner's "5m enough".

### Verify
- Offline-create 20 donations → airplane off → auto-sync within ~10 s on uploader, ≤5 min on peer; clock-skew row (old `updatedAt`) still arrives; DENIED row doesn't block others; Full resync recovers a stuck device.

## F4 — Real names in Team & Counters

- Stamp `counterName = entered counter ?: Google displayName ?: Counter-XXXX` on join + every presence touch (backfills silently, no migration).
- Inline counter-name field in Join (pre-filled from Google name); `resolveMemberName` unchanged as renderer.
- Privacy: ledger `deviceId` → last-4 tag only (`FirestoreMappers.donation/expenseToMap` default `""` → pass `prefs.deviceId.takeLast(4)`); `myRole()` checks `myStatus == revoked` before admin override.

### Verify
- New joiner with no counter set shows Google name + `#tag`, never `Counter-XXXX`; revoked admin loses head powers immediately.

## F5 — Sarvam auto-share (global key, no rules change)

- Auto-`readTtsKey()` on sign-in success + after each successful `syncEvent` + on Admin open (debounced); persist key→`SecureKeyStore`, speaker→prefs, mode→`SARVAM_CLOUD`; pill "Shared voice: Priya (cloud, synced h:mm)" vs "Local only — head hasn't published / pull failed".
- Publish stays master-admin-only server-side; UI already gates + falls back honestly. Per-event `events/{id}/config/voice` deferred (needs rules deploy + migration + per-event picker) — revisit post-festival.

### Verify
- Head publishes → collector signs in → voice pill flips to shared speaker with zero taps; key rotation propagates within one sync cycle.

## F6 — Voice: network-default Android voice + one-voice Sarvam queue

- `AndroidTtsClient`: after te-IN settle, pick best voice = first `te` **network** voice (Google TTS high-quality) → embedded te-IN → system. Only when `nativeTtsVoice == null` (never clobbers picks/presets). Extend voice list to `(name, isNetwork, quality)` for picker badges; network-offline speak error falls back to embedded for that utterance.
- `DualTtsEngine`: new `playPhraseBest(text, cacheKey)` — Sarvam `intro_{preset}_{speaker}.mp3` / `outro_{speaker}.mp3` in `audioDir`, served via `playFile` when cached/generable (1 quota call each, then cached for weeks), else native. Prefetch generates them on preset/speaker/key change.
- Existing Sarvam/imported donation caches untouched (no re-download storm).

### Verify
- Fresh install with Google TTS: default voice is a network te-IN voice (picker badge shows "Network"); airplane mode still speaks (embedded fallback).
- Sarvam queue: intro + names + outro all in Priya; quota pill counts intro/outro calls; offline mode uses one Android voice throughout.

---

## Build checklist (per phase)
`assembleDebug` + `testDebugUnitTest` green · two-device matrix (publish → zero-event join → approve → both-directions sync → revoke) · airplane counter loop · WhatsApp gallery-QR join · voice pill + full-Sarvam queue on horn speaker · update `PROGRESS.md` + `SESSION_HANDOFF.md` + `ARCHITECTURE.md` §4.4/§7 in the same task.

## Suggestions (non-blocking, owner call)
1. Delete-dummy prompt after join (prevents event-list clutter).
2. Show `pending`/`revoked` banner inside gear Team section (today only Dashboard/DonationEntry).
3. Cap first-sync pull with `limit()` paging (protects 1000+ row festivals from OOM).
4. F2b camera scan only if field feedback says typing/gallery is slow — code point reserved in `TeamSyncSection`.
