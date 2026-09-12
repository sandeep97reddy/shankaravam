# ShankaRavam — Festival Organizer App

Offline-first Android app for Indian temple & festival committees (Vinayaka Chavithi, Sri Rama Navami, Dasara, Hanuman Jayanthi, renovation funds…).

Volunteers at temple counters and festival pandals use it to **record donations in seconds, announce them in Telugu over a Bluetooth horn speaker, track expenses, and see the live balance** — with **zero login and zero internet required**.

## ✨ Features

- 🛕 **Events** — one profile per festival/fund (name, temple, dates, QR invite code).
- 💰 **Donations** — cash, UPI, bank, cheque + material (rice, sarees, flowers, sponsorship, service). Pledged / Received / Confirmed / Cancelled statuses, tags, duplicate guard.
- 🔊 **Telugu announcements** — natural templates (`శ్రీ {పేరు} గారు … {మొత్తం} రూపాయలు…`), offline Android voice + optional Sarvam AI cloud voice, Bluetooth amplifier support, play/pause/skip/repeat queue, temple-bell chime.
- 🧾 **Expenses** — categories (flowers, annadanam, sound, transport…), WebP receipt photos, cancel-keeps-history.
- 🛡️ **Safe ledger** — typo grace window, then correction-transactions (original preserved + delta + reason). Nothing is ever silently deleted.
- 📊 **Dashboard & reports** — live totals, balance (`confirmed + received cash − expenses`), PDF / CSV / WhatsApp summary, activity history.
- ☁️ **Optional team sync** — Google Sign-In + Firestore delta sync across counters (off by default). Works forever offline if you never turn it on.
- 🌐 **English + తెలుగు** UI.

## 🚀 How it works (simple steps)

1. Open the app → spinning Chakra splash (≤1.5s) → Dashboard.
2. Create an event (e.g. *Vinayaka Chavithi 2026*). It stays selected in the banner so entries never land in the wrong event.
3. Tap **+ Donation** → name, amount *or* item, payment method, status → Save. Done offline, instantly.
4. Tap **Announce** → queue of received/confirmed donations → Play. Audio goes to the Bluetooth horn speaker (or phone speaker). Test-audio button included.
5. Tap **+ Expense** for money-out (with optional receipt photo).
6. Check **Dashboard** for totals + balance; **Reports** for PDF/Excel/WhatsApp share.
7. (Optional, multi-counter only) **Settings → Cloud Sync** → sign in → share QR/code → teammates join → auto-sync in background.

## 🧱 Tech (short)

Kotlin + Jetpack Compose (Material 3) · Room (offline source of truth) · StateFlow · WorkManager sync · Android `te-IN` TTS + Sarvam AI · Firebase Auth/Firestore (Spark, optional). Details: `ARCHITECTURE.md`.

## 🛠️ Build & run

```bat
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

- Requires Android Studio (JVM 17), `compileSdk 36`, `minSdk 26`.
- No `google-services.json` needed for offline builds — cloud features simply stay inert until you add it.
- Deploy rules (team mode): `firebase deploy --only firestore:rules`

## 👥 Roles

| Role | Can do |
|---|---|
| Global Head | everything: events, team approvals, role changes, revoke, close event, cloud voice key |
| Collector (`organizer`) | add donations/expenses, announce, export, approve join requests |
| Viewer (`member`) | view totals, submit expense receipts, request collector access |

Fresh install starts as an offline Collector — no account needed.

## 🤖 Explain this app with AI (copy-paste prompt)

> Paste **this section + the whole README** into ChatGPT, Gemini, Claude, or any chatbot to get a plain-language walkthrough.

```text
You are a friendly explainer for a non-technical temple volunteer.
Read the ShankaRavam README pasted below and explain how the app works
in simple steps.

Rules:
- Max 10 short steps, plain words, no jargon.
- Cover: create event, add cash donation, add material donation,
  announce in Telugu on speaker, add expense, check balance, share report.
- Explain that it works without internet and login is only needed for team sync.
- Offer a Telugu + English version if possible.
- End with one line on who to ask for help (the festival head / organizer).

README:
[PASTE THE FULL README TEXT BELOW THIS LINE]
```

## 📁 Docs

- `ARCHITECTURE.md` — technical architecture, data flows, invariants
- `AGENTS.md` — contributor rules (offline-first, free-tier, ledger safety)
- `festival organizer app plan.md` — full product spec
- `PROGRESS.md` / `SESSION_HANDOFF.md` — build status & handoff
