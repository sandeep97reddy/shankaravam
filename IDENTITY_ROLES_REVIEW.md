# Identity, Cards & Roles Review — Findings + Plan
*(For cross-verification by another agent. Read-only research; no code changed. Compiled 2026-09-13.)*

## 0. Vocabulary (agreed by all three layers — keep as truth)

- Stored roles: `global_head` | `organizer` | `member` (`SessionPrefs.ROLE_*`, `Membership.UserRole`, `firestore.rules:41,64,111-121`)
- Stored statuses (orthogonal): `active` | `pending` | `revoked`
- UI labels only: Collector→`organizer`, Viewer→`member`. Never persist UI words — a persisted `"collector"`/`"viewer"` demotes to `MEMBER` via `roleOf` fallback (`Membership.kt:9-10`, fail-closed but confusing; no writer does this today).
- Defaults asymmetry (known, fail-closed): absent-key `myRole`→`ORGANIZER` (`SessionPrefs.kt:232`, offline never degrades) vs `roleOf(null/garbage)`→`MEMBER` vs `UiState` defaults `"organizer"` (`CloudSyncScreen.kt:102`).

---

## 1. Roles × permissions audit (client vs `firestore.rules`)

| # | Action | Client gate | Rules gate | Verdict |
|---|---|---|---|---|
| 1 | Read ledger | No gate — Room is truth (`DonationListViewModel.kt:43-75`, `ExpenseListViewModel.kt:49-79`) | Active member or creator (`rules:79,84,89,94`); pending/revoked denied; header read wider for joiners (`:72-73`) | By-design divergence (offline-first). Pull failures surface as generic `Outcome.Err` (`FirestoreSyncService.kt:127-130`) |
| 2 | Write donation/expense + corrections create | **No pre-flight gate.** `DonationEntryViewModel.kt:105-154`, `ExpenseEntryViewModel.kt:123-149`. `canAddDonation` has zero callers (dead policy); `canAddExpense` returns `true` for ALL roles (`Membership.kt:22-24`) | `canWriteLedger` = active head/organizer/creator (`:80,85,90`, `:39-42,52-54`). Viewer/pending/revoked denied. Activity create wider (any active, `:95`) | CLIENT-ALLOWS-BUT-RULES-DENY (fail-closed; row stays `PENDING_UPLOAD`). `canAddExpense=true` for MEMBER contradicts rules — fix or delete |
| 3 | Correct donation / cancel+correct expense | Role-only: `DonationDetailViewModel.kt:44`, `ExpenseListViewModel.kt:89,112` (`role!=MEMBER`) | Same `canWriteLedger` — needs **active** status | CLIENT-ALLOWS-BUT-RULES-DENY on status axis; match on role axis |
| 4 | Approve pending→collector/viewer | Card if `canApproveMembers(role)` (`CloudSyncScreen.kt:580`); `approve()` VM ungated (`:291-307`); correct stored literals `"organizer"`/`"member"` (`:704,708`) | Any active collector incl. organizer (`:60-65`, S1.2 offline-head decision) | Match on role; CLIENT-ALLOWS on status (pending/revoked organizer sees card, fails server-side) |
| 5 | Promote to head / edit active member | `isHeadNow` gate (`:321-323,351`); UI exposes only Collector/Viewer/Revoke, but `setMemberRole` service accepts `global_head` (`FirestoreSyncService.kt:375-417`) with no creator check | Only creator or master admin (`:123-131`; approve path excludes heads `:62-64`) | CLIENT-ALLOWS-BUT-RULES-DENY for non-creator heads; reverse PRE-SEAT-ADMIN gap (denied-client/allowed-server, partly mitigated by head fold `SessionPrefs.kt:229-232` + `AuthRepository.kt:53-64`, but not for local-only events) |
| 6 | Revoke | Same `isHeadNow`; dialog sends (role unchanged, revoked) (`:806`) — correct contract | Same as #5 (revoke never qualifies for approve path `:63`) | Same dual mismatch as #5 |
| 7 | Read team roster | `refreshMembers` head-gated (`:326`); tab + auto-load head-only (`:409-412,541,543-560`) | Any active member incl. viewer can read all docs (`:105-106`) | RULES-ALLOW-BUT-CLIENT-HIDES (intentional S4 head-only directory — document, don't "fix" without a read-budget decision) |
| 8 | Write `config/tts_settings` | `canManageKeys(role) && isGlobalHeadEmail(email)` (`AdminSettingsScreen.kt:546-547`) | Write = master admin only (`:156`, `:19-25`); read = any signed-in (`:155`) | Match for normal heads. Gap: whitelisted admin on local-only event fails client button though server would accept |
| 9 | Close event | Visible if `canCloseEvent(role)` (`:556`, role-only); `closeEvent` VM ungated (`:309-325`); cloud via next header `set` (`:64-69`) | `canWriteLedger` (`:75`); **no closed-immutability** — post-close writes still pass, re-open possible | Match on role; CLIENT-ALLOWS on status (local close diverges from cloud). CLOSED is advisory everywhere — entry VMs never check `Event.status` |
| 10 | Invite codes | `publishCode` ungated + local head-seat bootstrap (`:210-232,215-217`); `closeCode` head-gated (`:240`); expiry client-side only (`:281-287`) | Create = any signed-in w/ `createdBy==self` (`:141-142`); update = creator/admin (`:143-144`); no server expiry check | Publish: match. Close: CLIENT-ALLOWS-BUT-RULES-DENY for non-creator heads. Open-invite by design |

### Cross-cutting flags
- **A. Status-vs-role (biggest systemic gap).** `AccessPolicy.*` takes only `UserRole` (`Membership.kt:22-32`); all callers pass `roleOf(myRole)` without `myStatus`. Only `isHeadNow` checks both. `ADMIN_HEAD_PLAN.md:155,158` "revoked stays on device" UX is not implemented on write/sync-failure paths; `RevokedAccessBanner` (`AccessBanner.kt:36-46`) is revoked-only, snapshot-based, placed on Dashboard + DonationEntry only; no pending banner exists.
- **B. Correct wirings to preserve.** `requestToJoin` pending/member vs admin auto-head (`:306-327`); pending-joiner header read (`rules:72-73` + best-effort pull); merge-writes preserving `joinedAt`/presence; self-touch equality pins (`:126-131`); `memberStatusOf` pending-default (`Membership.kt:9-16`); creator deadlock fix (`:47-50`).

---

## 2. Identity / attribution trace (names already work — no arch replacement needed)

### 2.1 Sources (`SessionPrefs.kt`)
- `googleDisplayName`/`googleEmail` plain prefs, sole writer `AuthRepository` (`:56-57,99-100,112-113`).
- `attributionName()` (`:58-76`): `counter • Name (email)` | counter | `Name (email)` | `Counter-XXXX`. Stored verbatim in ledger.
- `rawCounterName()` (`:83-86`): counter or `Counter-XXXX`, no PII — for every Firestore write.

### 2.2 Ledger stamping (offline → Room → Firestore, all verbatim, no sync-time rewrite)
- `Donation.addedBy` / `Expense.addedBy`: entry VMs (`DonationEntryViewModel.kt:105,109,143`; `ExpenseEntryViewModel.kt:123,126,140`) → use-cases → Room (`EntityMappers.kt:48,61,70,80`) → `donationToMap`/`expenseToMap` (`FirestoreMappers.kt:19,33,71,79-80`, incl. full `deviceId` despite the "never leaves Room" comment at `FirestoreSyncService.kt:28` — note discrepancy) → `updateSyncState` skipped on deny so rows stay `PENDING_UPLOAD` (`:74,81,87`). Download symmetric (`:60,101` → Room `SYNCED`).
- `Expense.paidBy`: free-text form field, untouched (`ExpenseEntryViewModel.kt:43,135`; `FirestoreMappers.kt:76,96`).
- `Correction.correctedBy`: `RecordCorrectionUseCase.kt:30,47` via `CorrectRecordUseCase.kt:29,46`; callers stamp `attributionName()` (`DonationDetailViewModel.kt:38-47`; `ExpenseListViewModel.kt:111-127`). Upload-only, no pull path.
- `Activity.actorId`: money paths copy ledger stamp (`SaveDonationUseCase.kt:82`; `SaveExpenseUseCase.kt:60`; `RecordCorrectionUseCase.kt:58`); cancel/close/create stamp `attributionName()` — **except `EVENT_CLOSED` stamps raw UID** (`AdminSettingsScreen.kt:319` — fix to `attributionName()`). Activities never sync (no mapper, no collection; pulls only donations/expenses `:91-92`).

### 2.3 Members/presence (only place Google name hits Firestore)
- `memberToMap` (`FirestoreMappers.kt:169-189`); per-sync touch (`FirestoreSyncService.kt:99-122`, doc = `me.uid`); `requestToJoin` (`:269-319`, `effectiveEmail/Name`, `rawCounterName` at `:314`); `publishShareCode` (`:191-232`); `setMemberRole` (`:375-417`, UID as doc-id + `approvedBy`); `audioGeneratedBy = uid ?: attributionName()` (`AnnouncementQueueViewModel.kt:679-691`); `writeTtsKey updatedBy=uid` (`:469-480`).

### 2.4 Google name does NOT replace UID at sync — and doesn't need to
Ledger rows never contain UIDs (old rows keep pre-login stamps — correct audit behavior). Raw UIDs persist by design in doc-IDs, `approvedBy`, `createdBy`, `globalHeadId`, `audioGeneratedBy`, `updatedBy` — none rendered except the 4 leaks below.

### 2.5 User-visible UID leaks to fix
1. `CloudSyncScreen.kt:702` ApprovalsCard: `userId.take(12)+…` ignores fetched `counterName/displayName/email`.
2. `CloudSyncScreen.kt:852` TeamRow subtitle: UID last-6 when `email==null` (title `:825-829` is safe).
3. `ActivityFeedScreen.kt:189,200,211 → :512-526`: `CORRECTION_ADDED/EVENT_CREATED/RECORD_CANCELLED` render `actorId` directly — only `EVENT_CLOSED` stamps UID today.
4. `CloudSyncScreen.kt:471` account fallback prints full UID if name+email null.

---

## 3. Cards today vs requested behavior

| Screen | Shows today | Click today | Expansion anchor (exact) |
|---|---|---|---|
| History `RichTransactionCard` (`ActivityFeedScreen.kt:405-553`) | Collector line `:512-527` (`donation/expense.addedBy ?: actorId`; corrections/events render raw `actorId` `:189,200,211`) | Nothing — no `onClick` (`:413-419`) | Add `expanded/onClick` params; `AnimatedVisibility` inside `Column(:420)` after bottom `Row(:550)`; hoist state at `:274-278` + `items(:379)` |
| Expense `ExpenseCard` (`ExpenseListScreen.kt:209-276`) | `paidBy` only (`:267-273`, hardcoded English, no icon); ignores `addedBy` | Nothing on card; correct/cancel icon buttons only (`:259,262`) | Add `expanded/onToggle`; block inside `Column(:223)` after footer `:273`; hoist `expandedId` at `:64-69`, wire at `:150-156` |
| Donation `DonationCard` (`DonationListScreen.kt:232-315`) | `Collector: addedBy` (`:269-287`) | Opens bottom sheet (`:163-168,175-183`) | Add `expanded` block inside `Column(:245)` after tags (`:306-312`); state near `selectedId (:73)`; needs tap-vs-sheet decision |
| Detail sheet (`DonationDetailSheet.kt`) | `Added by` row (`:93`); `CorrectionHistory (:331-350)` hides `correctedBy` | Static rows (`DetailRow :323-328`) | Sheet already expanded-context; optional per-correction expand at `:343-348` |

---

## 4. Implementation plan (phases, files, verify)

- **P1 — Expandable cards.** `ActivityFeedScreen.kt` (~:405-420,550), `ExpenseListScreen.kt` (~:209-223,273), `DonationListScreen.kt` (~:232-245,312). Detail block: collector/paid-by/added-by, event creator (`events.globalHeadId` resolve best-effort), timestamp, sync status. `animateItem()` keys already present; add `AnimatedVisibility` + `Role.Button` semantics.
- **P2 — Identity resolution.** New `resolveMemberName(member)` = `counterName → displayName → email → short-ID`; apply to ApprovalsCard (`:702`), TeamRow subtitle (`:852`), history collector lines; fix `EVENT_CLOSED` actor (`AdminSettingsScreen.kt:319` → `attributionName()`).
- **P3 — Status-aware gating.** New `canWriteMoney(eventId)` (active + head/organizer); call at `DonationDetailViewModel:44`, `ExpenseListViewModel:89,112`, entry VMs, `AdminSettingsScreen:556`, `CloudSyncScreen:580`; fix `canAddExpense` (`Membership.kt:24`); revoked/pending-specific sync-failure copy + banner on expense/correct paths.
- **P4 — Verify.** `.\gradlew.bat assembleDebug` + `testDebugUnitTest`; two-account matrix (viewer write denied-but-local, revoke banner, close-event divergence, rejoin flow). Update `PROGRESS.md` + `SESSION_HANDOFF.md`.

## 5. Open questions for the owner
1. Donation tap = inline expand (sheet moves to explicit button) or keep sheet + second gesture for expand?
2. CLOSED events: hard-block new entries client-side or stay advisory?
3. Roster read: open to active collectors (rules already allow) or keep head-only?

## 6. Implementation status (2026-09-13) — cross-verified verdict: 100% GENUINE
Second agent confirmed every finding, reference, and line number. Open questions resolved per verdict:
- **Q1 → keep bottom sheet** on donation tap; inline expand only for History + Expense (done as specified).
- **Q2 → hard-block implemented.** Entry VMs expose `isEventClosed` (`DonationEntryViewModel`, `ExpenseEntryViewModel`); `save()` refuses with an error state; screens show `ClosedEventBanner` (new `closedEventTitle/Body` EN+TE in `AppStrings.kt`) and disable the save button.
- **Q3 → read-only roster implemented.** `canViewRoster(eventId)` (signed-in + active, any role); tabs + auto-load gated on it; `TeamRow` action row hidden unless `manageEnabled=isHeadNow`. `setMemberRole`/`approve` still head-gated.

Shipped in this batch:
- **P1:** `RichTransactionCard` + `ExpenseCard` expand on tap (`AnimatedVisibility` downward block: full collector, event name via new `ActivityFeedViewModel.eventName` flow, paid-by/vendor/pronunciation `detail`, recorded-at, sync status). Donation card untouched per Q1.
- **P2:** `resolveMemberName()` (`Membership.kt`) + `MemberNameTest` (4 tests); ApprovalsCard/TeamRow/account UID leaks fixed; `EVENT_CLOSED` now stamps `attributionName()`.
- **Verify:** `assembleDebug` + `testDebugUnitTest` 90/90 green (09:34). No `firestore.rules` change needed (client gates moved inside existing server boundaries).
- **Not implemented (left as documented gaps):** full P3 status-aware gating (`canWriteMoney`, `canAddExpense` fix), server-side closed-event immutability, full-`deviceId` in ledger maps (see §2.1 note).
