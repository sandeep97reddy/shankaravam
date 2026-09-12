# ShankaRavam — Master Admin Whitelist, Security Hardening & Team Directory (REVISED PLAN)

Source: original proposal "Master Admin Email Whitelist" + deep review 2026-09-12 (10× P0 findings folded in below — nothing from the original plan was dropped, only hardened).
Build order: **S1 → S5, strictly in sequence.** Each phase is independently verifiable and shippable. Stop if its Verify fails.

Whitelisted Master Email: `sandeepreddyr97@gmail.com` (single source; rules + client change together — see S1.1).

## Locked decisions (do not re-litigate)

1. **Rule #1 preserved:** fresh install = offline `ORGANIZER` (Collector Mode), zero login. Team directory is head-only and never fetched for volunteers.
2. **Least privilege by default:** unknown/corrupt role strings fall back to `MEMBER` (viewer), never `ORGANIZER`. Revoke is a **status** (`active/pending/revoked`), never a role string.
3. **Client email check is UI-only.** Firestore Rules are the security boundary and must pin `sign_in_provider == 'google.com'` + `email_verified == true` (email claim alone is spoofable via email/password provider).
4. **`/config/tts_settings` stays global singleton** until a per-event config path exists. S1 decides (a) global-admin-only key vs (b) per-festival heads keep publishing — no silent breakage.
5. **Member docs are merge-written.** No bare `.set()` on members after S2 (wipes identity/presence + resets `joinedAt`).
6. **Role vocabulary (stored):** `global_head` = Head, `organizer` = Collector (money+announce), `member` = Viewer (totals only). UI labels map: Collector→`organizer`, Viewer→`member`. Status is orthogonal: `active/pending/revoked`.
7. **Presence is best-effort, lossy, merge-only.** Never fails a ledger sync. Full `deviceId` UUID never leaves the device — only `deviceTag` (last4) is stored.

---

## S1 — Rules hardening + privilege-by-default fix (security first, deployable alone)

**Goal:** close spoof / escalation / hijack holes before any client depends on them.

### Files
- `firestore.rules` (rewrite § members/codes/config + `isGlobalAdmin`)
- `domain/model/Membership.kt:6-7` (`roleOf` fallback → `MEMBER`)
- `app/src/test/.../domain/model/AccessPolicyTest.kt:44` (assert fallback = MEMBER)

### Steps
1.1 Add hardened helper (replaces naive email check):
```javascript
function isGlobalAdmin() {
  return signedIn()
    && request.auth.token.email != null
    && request.auth.token.email.lower() == 'sandeepreddyr97@gmail.com'
    && request.auth.token.email_verified == true
    && request.auth.token.firebase.sign_in_provider == 'google.com';
}
```
1.2 Members — head/creator manage + self presence-touch carve-out (fixes presence deadlock from review §P0-2):
```javascript
match /members/{userId} {
  allow read: if signedIn() && (isActiveMember(eventId) || request.auth.uid == userId || isGlobalAdmin());
  allow create: if signedIn() && request.auth.uid == userId && (
    (request.resource.data.status == 'pending' && request.resource.data.role == 'member')
    || (isEventCreator(eventId) && request.resource.data.status == 'active' && request.resource.data.role == 'global_head')
    || (isGlobalAdmin() && request.resource.data.status == 'active' && request.resource.data.role == 'global_head')
  );
  allow update: if isGlobalAdmin() || isEventCreator(eventId)
    || (
      signedIn() && request.auth.uid == userId
      && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['lastActiveAt','counterName','deviceTag','displayName'])
      && request.resource.data.role == resource.data.role
      && request.resource.data.status == resource.data.status
    );
  allow delete: if false;
}
```
Open question answered here: organizers **lose** approve powers under this rule. If that is not intended, keep `canWriteLedger(eventId)` for approve but add field-level `diff` pinning so role/status changes stay head-only — decide in this phase, update `AccessPolicy.canApproveMembers` + `CloudSyncScreen ApprovalsCard` gate to match (no client/server mismatch).
1.3 Codes — anti-squat + anti-overwrite:
```javascript
match /codes/{code} {
  allow read: if signedIn();
  allow create: if signedIn() && request.resource.data.createdBy == request.auth.uid;
  allow update: if isGlobalAdmin() || (signedIn() && resource.data.createdBy == request.auth.uid);
  allow delete: if false;
}
```
Client must use create-only semantics for first publish (transaction or `get`+`create`); second publisher's `.set()` is an `update` and correctly fails unless creator/admin.
1.4 TTS key — pick ONE and update UI to match (review §P0-5):
- (a) Global-key model: `allow write: if isGlobalAdmin();` + hide "Publish for collectors" from non-admin heads, or
- (b) Per-festival model: needs per-event config path (not this plan — defer; keep current head-write rule + document that global lock is postponed).
1.5 `roleOf` fallback → `MEMBER`; fix test. This is the privilege-by-default seal.

### Verify S1
- `firebase deploy --only firestore:rules --dry-run` (or emulator) green.
- Manual rules matrix (emulator or two test accounts): collector self-promote denied; non-Google-provider email denied; unverified denied; code overwrite by non-creator denied; revoked write denied; self `lastActiveAt`-only touch allowed; role/status change by collector denied.
- `.\gradlew.bat testDebugUnitTest` green (AccessPolicy fallback test updated).
- **Stop rule:** do not start S2 until matrix passes.

---

## S2 — Domain + data foundation (no UI yet)

**Goal:** types, mappers, prefs, and auth wiring that later phases build on — with merge-write safety.

### Files
- `domain/model/Membership.kt` — add `AdminConfig` + `MemberPresence` + head gates
- `data/remote/FirestoreMappers.kt:162-163` — extend `memberToMap`, add `memberFromMap`
- `data/remote/FirestoreSyncService.kt:19-25` — extend `CloudMember`
- `data/local/SessionPrefs.kt:151-157` — add `isGlobalHeadUser`-equivalent **scoped** + `myStatus(eventId)`
- `data/remote/AuthRepository.kt:32-48,66-81` — inject `SessionPrefs`, set/clear flag in listener
- `di/AppContainer.kt:94-96` — wire `AuthRepository(appContext, sessionPrefs)`

### Steps
2.1 `AdminConfig` (single source for the email; `Locale.ROOT`):
```kotlin
object AdminConfig {
  const val GLOBAL_HEAD_EMAIL = "sandeepreddyr97@gmail.com"
  fun isGlobalHeadEmail(email: String?): Boolean =
    !email.isNullOrBlank() && email.trim().lowercase(java.util.Locale.ROOT) == GLOBAL_HEAD_EMAIL
}
enum class MemberPresence { ACTIVE_NOW, IDLE, OFFLINE }
fun presenceOf(lastActiveAt: Long, now: Long = System.currentTimeMillis()): MemberPresence = when {
  lastActiveAt <= 0L -> MemberPresence.OFFLINE
  now - lastActiveAt < 15*60*1000L -> MemberPresence.ACTIVE_NOW
  now - lastActiveAt < 2*60*60*1000L -> MemberPresence.IDLE
  else -> MemberPresence.OFFLINE
}
// AccessPolicy additions:
fun canViewTeamRoster(role: UserRole): Boolean = role == UserRole.GLOBAL_HEAD
fun canManageMembers(role: UserRole): Boolean = role == UserRole.GLOBAL_HEAD
```
2.2 `CloudMember` — store tag, not full ID:
```kotlin
data class CloudMember(
  val userId: String, val role: String, val status: String,
  val approvedBy: String = "", val joinedAt: Long = 0L,
  val email: String? = null, val displayName: String? = null,
  val counterName: String? = null, val deviceTag: String? = null, // last4 only
  val lastActiveAt: Long = 0L
)
```
`memberToMap` gains optional params with defaults (old call sites compile); add `memberFromMap(docId, map)` handling missing keys (pre-migration docs). Never write full `deviceId`.
2.3 `SessionPrefs` — scoped override + status cache (fixes review §P0-8/P0-3):
- `isGlobalHeadUser: Boolean` (`is_global_head_user`), set/cleared ONLY in `AuthRepository` listener.
- `myStatus(eventId): String` (`member_status_<id>`, default `"active"` for offline local events, `"pending"` after join until approved).
- `myRole(eventId)`: if `myStatus == "revoked"` → caller gates writes off (do NOT return fake role); else if `isGlobalHeadUser && <event is cloud-joined>` → `ROLE_GLOBAL_HEAD`; else stored role (default `ROLE_ORGANIZER` offline). Never return head for local-only events.
2.4 `AuthRepository(appContext, sessionPrefs)` — in `AuthStateListener` + `handleSignInResult`: `sessionPrefs.isGlobalHeadUser = AdminConfig.isGlobalHeadEmail(user?.email)`; on null user → `false`. Diff before writing. `signOut()` itself doesn't touch the flag (listener owns it).
2.5 Convert ALL member writes to `SetOptions.merge()` (publish/join/approve paths). Preserve original `joinedAt` on updates (exclude the key on update path).

### Verify S2
- `.\gradlew.bat assembleDebug` + `testDebugUnitTest` green.
- Unit-test `memberFromMap` with legacy 4-key doc (no crash, nulls defaulted).
- **Stop rule:** no UI work until merge-write conversion compiles and legacy-doc test passes.

---

## S3 — Membership flows (join, presence, roster, revoke enforcement)

**Goal:** correct server-observable behavior for every non-UI path.

### Files
- `data/remote/FirestoreSyncService.kt:139-253` (`publishShareCode`, `requestToJoin`, `pendingMembers`, `setMember`, `syncEvent`)

### Steps
3.1 `requestToJoin(code, uid)` — thread identity through (new params or read `Firebase.auth.currentUser` + `prefs` inside; prefer explicit params for testability: `email, displayName`):
- Normalize code; `get codes/{code}` → eventId.
- Header pull becomes **best-effort** (joiner read may deny per review §P0-6 — do not fail join on it; upsert Room only on success).
- If `AdminConfig.isGlobalHeadEmail(email)` → `set(merge, role="global_head", status="active", ...)` (rules §create clause 3 allows it); else `role="member", status="pending"`. Stamp `counterName=prefs.counterName or attributionName()`, `deviceTag=prefs.deviceId.takeLast(4).uppercase()`, `displayName`, `email`, `lastActiveAt=now`.
- Local: admin → `setMyRole(global_head)+setMyStatus(active)`; else → `setMyRole(member)+setMyStatus(pending)`.
3.2 `syncEvent(eventId)` presence touch — **separate best-effort block AFTER ledger sync, never failing the result**: `members/{myUid}` `set(merge, lastActiveAt+counterName+deviceTag+displayName)`. Swallow `PERMISSION_DENIED` (pending/revoked) silently. Document +1 write/sync/device (Spark-safe at festival scale).
3.3 Roster reads — replace `pendingMembers` + new `fetchAllMembers` with ONE `fetchAllMembers(eventId)` (all docs `orderBy joinedAt`) + client-side `partition { status=="pending" }`. Gate the call: ViewModel only invokes when head-gated. Note: needs NO composite index (single orderBy); keep old composite-index query deleted to avoid `FAILED_PRECONDITION`.
3.4 Role management — unify as `setMemberRole(eventId, userId, role, status, approvedBy)` (replaces `setMember`); exact pairs: Collector=`(organizer,active)`, Viewer=`(member,active)`, Revoke=`(keepRole,revoked)`, Approve-pending→Collector/Viewer. Merge-write, preserve `joinedAt`.
3.5 Revoked/pending enforcement — on sync (and on `CloudSyncViewModel` refresh), re-read own member doc; update `myStatus`/`myRole` cache; UI gates treat `revoked/pending` as read-only for money actions with an explanatory notice (offline entry still allowed locally per Rule #1? — decide: local Room writes stay possible, cloud writes will deny; surface "Access revoked — changes stay on this device").

### Verify S3
- Two-device manual: head publishes → joiner pending appears; approve→Collector syncs both ways; revoke blocks ledger writes (`PERMISSION_DENIED` in log, friendly notice, no crash); admin-email join auto-activates.
- Presence: second device appears `ACTIVE_NOW` within one sync cycle; airplane-mode device ages to `IDLE/OFFLINE` without errors.
- **Stop rule:** S4 UI must never be built on unproven flows.

---

## S4 — Global Head UI: Connected Team & badges (head-only, perf-clean)

**Goal:** exclusive directory component; volunteers see zero change.

### Files
- `presentation/settings/CloudSyncScreen.kt:76-237,376-386` (VM: `_teamMembers`, `refreshMembers`, `setMemberRole`; UI: `ConnectedCountersCard`)
- `presentation/settings/AdminSettingsScreen.kt:291-302,411-417` (role card + Publish gating)

### Steps
4.1 VM: add `_teamMembers: StateFlow<List<CloudMember>>`, `refreshMembers(eventId)` (head-gated, error maps `PERMISSION_DENIED`→"Only the head can view the team"), `setMemberRole(...)` with per-member busy keys (`busy=="role:<uid>"`), updating `_teamMembers` in place on success. Keep `pending` as derived partition (no second query).
4.2 `ConnectedCountersCard` — visible ONLY when `roleOf(state.myRole)==GLOBAL_HEAD && myStatus=="active"` (server-confirmed head preferred over raw email check). Never fetch when hidden. Contents per spec: presence badge (`presenceOf`, recomputed via `derivedStateOf` + refresh-on-resume timer, not per-frame), identity line `counterName ?: "Unknown counter"` + `#deviceTag`, email/displayName secondary, role badge (Head gold / Collector emerald / Viewer slate / Pending gold / Revoked red-grey), 1-tap menu (Make Collector / Make Viewer / Revoke) with confirm for Revoke.
4.3 Perf (skill: jetpack-compose-performance): `LazyColumn key={it.userId}`, `Modifier.animateItem()`, `@Immutable CloudMember`, presence via `remember(members){ derivedStateOf{...} }`, no `memberToMap`/time-math inside composition.
4.4 Account card: `👑 Global Head Admin • <email> (Verified)` ONLY when email-whitelisted AND own member doc is `active/global_head`; else plain email line. AdminSettings role card mirrors this; Publish button gating follows the S1.4 decision (hide for non-admin heads under model (a)).

### Verify S4
- Signed-out volunteer: card absent, zero `members` reads (check logcat/Firestore usage), donate+announce offline unaffected.
- Head: roster renders, actions work, revoked member flips badge without manual refresh after `refreshMembers`.
- 60/120fps: rapid approve/revoke bursts animate without jank (visual check + `animateItem` present).

---

## S5 — Tests, docs & release gate

### Files
- NEW `app/src/test/.../domain/model/AdminConfigTest.kt`
- `PROGRESS.md`, `SESSION_HANDOFF.md`, `firestore.rules` header comment (deploy command)

### Steps
5.1 Unit tests (pure JVM, no Context — keep `SessionPrefs` OUT of this file; test the pure fns):
- Email match: exact, upper-case, padded whitespace, null/blank, non-listed → false.
- `canViewTeamRoster/canManageMembers`: head true, organizer/member false (documents the S1.2 approve decision if organizers keep approve but lose team-manage).
- `presenceOf`: boundaries (0→OFFLINE, 14:59→ACTIVE, 15:00→IDLE, 1:59→IDLE, 2:00→OFFLINE, future timestamp→ACTIVE_NOW).
- Mapper: legacy 4-key doc → defaults; round-trip new doc preserves tag/presence; full deviceId never emitted.
- `roleOf`: unknown/`"revoked"`/`"collector"` → MEMBER (locks the P0-3 fix against regression).
5.2 `SessionPrefs` status/role test — ONLY if Robolectric already present; otherwise cover via fake-backed refactor in a follow-up (do not add the dep in this plan).
5.3 Release gate:
```
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
firebase deploy --only firestore:rules --dry-run   # then real deploy
```
Manual matrix (§Verification Plan in original proposal, items 1–4) + rules matrix from S1 + free-tier audit (no audio in maps, delta-only, WebP receipts, presence writes counted).

### Verify S5
- `testDebugUnitTest` all-green, debug APK builds, rules deployed, handoff docs updated (`PROGRESS.md` pointer + `SESSION_HANDOFF.md` §§1/3/4/5).

---

## Appendix A — (role,status) contract (normative)

| Action | role | status |
|---|---|---|
| Admin-email join | `global_head` | `active` |
| Normal join | `member` | `pending` |
| Approve as Collector | `organizer` | `active` |
| Approve as Viewer | `member` | `active` |
| Make Collector | `organizer` | `active` |
| Make Viewer | `member` | `active` |
| Revoke | *(unchanged)* | `revoked` |

Never store `role="revoked"` / `"collector"` / `"viewer"`.

## Appendix B — Cost & risk notes
- Presence: 1 merge-write per `syncEvent` per device (~96/day/device at 15-min cadence) — Spark-safe; lossy by design.
- Roster read: 1 query + N doc reads per refresh, head-initiated only.
- Rollback: rules redeploy previous version; client flag is local-only (sign-out clears). Old clients without merge-write must upgrade before S3 ships (or their approves wipe presence — enforce via version gate notice).
