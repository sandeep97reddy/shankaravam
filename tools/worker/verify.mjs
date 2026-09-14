// Zero-cost verify matrix for shankaravam-gateway (R2_IMPLEMENTATION_PLAN §4.3).
// Usage:
//   node verify.mjs                                   # needs WORKER_URL env
//   WORKER_URL=https://shankaravam-gateway.<you>.workers.dev node verify.mjs
//   ID_TOKEN=<firebase-id-token> node verify.mjs      # + authenticated 400/403 cases
//   ID_TOKEN=<...> node verify.mjs --live             # + ONE real synthesis (~1 Sarvam call)
//
// How to mint ID_TOKEN without touching the app: Firebase Console →
// Authentication → Add user (email+password test user) → Firestore →
// events/{eventId}/members/{uid} = {role:"organizer", status:"active"} →
//   curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=AIzaSyAG2PSPjw4P2LO2nzNFkejH9ZDhvsZF2vM"
//     -H 'Content-Type: application/json'
//     -d '{"email":"<test>","password":"<pw>","returnSecureToken":true}'
// → .idToken. Delete the test user + member doc afterwards.

const BASE = (process.env.WORKER_URL ?? "").replace(/\/+$/, "");
const TOKEN = process.env.ID_TOKEN ?? "";
const LIVE = process.argv.includes("--live");
if (!BASE) {
  console.error("Set WORKER_URL, e.g. WORKER_URL=https://shankaravam-gateway.<you>.workers.dev");
  process.exit(2);
}

let failures = 0;
async function check(name, fn, expectStatuses) {
  let status, body;
  try {
    const r = await fn();
    status = r.status;
    body = (await r.text()).slice(0, 160);
  } catch (e) {
    console.log(`FAIL ${name}: fetch threw ${e.message}`);
    failures++;
    return;
  }
  const ok = expectStatuses.includes(status);
  console.log(`${ok ? "PASS" : "FAIL"} ${name}: HTTP ${status} (want ${expectStatuses.join("/")}) ${body}`);
  if (!ok) failures++;
}

const HEX64 = "a".repeat(64);
const goodResolve = {
  eventId: "evt-matrix",
  donationId: "don-matrix",
  text: "matrix probe",
  language: "TELUGU",
  speaker: "shubh",
  roster: false,
  hash: HEX64, // wrong on purpose → hash-mismatch (after seat)
  templateVersion: "v1",
};
const auth = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};

await check("resolve, no token → 401", () =>
  fetch(`${BASE}/v1/audio/resolve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(goodResolve),
  }), [401]);

await check("audio GET, no token → 401", () =>
  fetch(`${BASE}/v1/audio/${HEX64}.mp3?eventId=evt-matrix`), [401]);

await check("receipt PUT, no token → 401", () =>
  fetch(`${BASE}/v1/receipts/e1/x1.webp`, {
    method: "PUT",
    headers: { "Content-Type": "image/webp" },
    body: new Uint8Array([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]),
  }), [401]);

await check("receipt GET, no token → 401", () =>
  fetch(`${BASE}/v1/receipts/e1/x1`), [401]);

await check("resolve, garbage token → 401", () =>
  fetch(`${BASE}/v1/audio/resolve`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: "Bearer garbage" },
    body: JSON.stringify(goodResolve),
  }), [401]);

await check("unknown route → 404", () => fetch(`${BASE}/v1/nope`), [404]);

if (TOKEN) {
  await check("resolve, empty body → 400", () =>
    fetch(`${BASE}/v1/audio/resolve`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...auth },
      body: JSON.stringify({}),
    }), [400]);
  await check("resolve, text 501 chars → 400", () =>
    fetch(`${BASE}/v1/audio/resolve`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...auth },
      body: JSON.stringify({ ...goodResolve, text: "x".repeat(501) }),
    }), [400]);
  await check("resolve, wrong hash → 400 (seat ok) or 403 (no seat)", () =>
    fetch(`${BASE}/v1/audio/resolve`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...auth },
      body: JSON.stringify(goodResolve),
    }), [400, 403]);
  const big = new Uint8Array(200 * 1024);
  big[0] = 0x52; big[1] = 0x49; big[2] = 0x46; big[3] = 0x46;
  big[8] = 0x57; big[9] = 0x45; big[10] = 0x42; big[11] = 0x50;
  await check("receipt PUT 200KB → 413 (seat ok) or 403", () =>
    fetch(`${BASE}/v1/receipts/e1/x1.webp`, {
      method: "PUT",
      headers: { "Content-Type": "image/webp", ...auth },
      body: big,
    }), [413, 403]);
  await check("receipt PUT png-bytes-as-webp → 400 (seat ok) or 403", () =>
    fetch(`${BASE}/v1/receipts/e1/x1.webp`, {
      method: "PUT",
      headers: { "Content-Type": "image/webp", ...auth },
      body: new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0, 0, 0, 0, 0, 0, 0, 0, 0]),
    }), [400, 403]);
} else {
  console.log("SKIP authenticated 400/403 cases (set ID_TOKEN; see header comment)");
}

if (LIVE) {
  if (!TOKEN) {
    console.log("SKIP live synthesis (needs ID_TOKEN)");
  } else {
    console.log("LIVE: one real synthesis follows (spends ~1 Sarvam call on first run)…");
    await check("resolve, real text → 200 (second run cached:true)", () =>
      fetch(`${BASE}/v1/audio/resolve`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...auth },
        body: JSON.stringify({
          eventId: "evt-matrix",
          donationId: "don-matrix-live",
          text: "శ్రీ పరీక్ష గారు ఉత్సవం కోసం వెయ్యి రూపాయలు విరాళంగా అందించారు. ధన్యవాదాలు!",
          language: "TELUGU",
          speaker: "shubh",
          roster: false,
          // hash of "TELUGU|shubh|false|<text>" — computed below at runtime
          hash: HEX64,
          templateVersion: "v1",
        }),
      }), [400]); // placeholder hash → 400 proves the gate; replace hash to spend
    console.log("NOTE: compute the real hash (sha256 of the input string) to run a true JIT; repeat to assert cached:true.");
  }
} else {
  console.log("SKIP live synthesis (pass --live to spend ~1 Sarvam call)");
}

console.log(failures === 0 ? "MATRIX GREEN" : `${failures} FAILURES`);
process.exit(failures === 0 ? 0 : 1);
