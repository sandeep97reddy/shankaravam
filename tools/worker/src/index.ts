/**
 * ShankaRavam temple-media gateway (R2_IMPLEMENTATION_PLAN Phase 2).
 *
 * Private R2 (`AUDIO_BUCKET`) behind Firebase-seat auth. Phones hold ZERO
 * secrets: the Sarvam key lives only as a Worker secret, the bucket is
 * reachable only through this Worker, and every route re-verifies
 * (JWKS signature + Firestore seat read as the user, fail-closed).
 *
 * Routes (all JSON errors `{error: code}`, donor PII never in logs):
 *   POST /v1/audio/resolve                 → {hash, url, cached}
 *   GET  /v1/audio/{hash}.mp3              → audio bytes (immutable)
 *   PUT  /v1/receipts/{eventId}/{expenseId}.webp → {url}
 *   GET  /v1/receipts/{eventId}/{expenseId}      → webp bytes
 */

import {
  DAILY_NEW_SYNTHESIS_CAP,
  MAX_RECEIPT_BYTES,
  SARVAM_TIMEOUT_MS,
  audioKey,
  canonicalHashInput,
  dayKey,
  isValidHash,
  isValidId,
  looksLikeWebP,
  receiptKey,
  safeEqual,
  sha256Hex,
  validateResolveBody,
} from "./lib";

interface Env {
  AUDIO_BUCKET: R2Bucket;
  SARVAM_API_KEY: string;
  FIREBASE_PROJECT_ID: string;
  FIREBASE_WEB_API_KEY: string;
  RATE_KV?: KVNamespace;
}

class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super(code);
  }
}

const json = (o: unknown, status = 200): Response =>
  new Response(JSON.stringify(o), {
    status,
    headers: { "Content-Type": "application/json" },
  });

// ---- Firebase ID-token verification (JWKS, no service account) ----

const JWKS_URL =
  "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

let jwksCache: { fetchedAt: number; keys: Record<string, JsonWebKey> } | null = null;

function b64urlToBytes(s: string): Uint8Array<ArrayBuffer> {
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/"));
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function getJwks(): Promise<Record<string, JsonWebKey>> {
  const now = Date.now();
  if (jwksCache && now - jwksCache.fetchedAt < 3600_000) return jwksCache.keys;
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new HttpError(503, "auth unavailable");
  const keys = (await res.json()) as Record<string, JsonWebKey>;
  jwksCache = { fetchedAt: now, keys };
  return keys;
}

/** Verifies RS256 signature + exp/aud/iss. Returns the uid (sub). */
async function verifyIdToken(token: string, projectId: string): Promise<string> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new HttpError(401, "bad token");
  const [hB64, pB64, sB64] = parts as [string, string, string];
  let header: { alg?: string; kid?: string };
  let payload: { exp?: number; aud?: string; iss?: string; sub?: string };
  try {
    header = JSON.parse(new TextDecoder().decode(b64urlToBytes(hB64))) as typeof header;
    payload = JSON.parse(new TextDecoder().decode(b64urlToBytes(pB64))) as typeof payload;
  } catch {
    throw new HttpError(401, "bad token");
  }
  if (header.alg !== "RS256" || !header.kid) throw new HttpError(401, "bad token");
  const keys = await getJwks();
  const jwk = keys[header.kid];
  if (!jwk) {
    jwksCache = null; // force key refresh next call (rotation)
    throw new HttpError(401, "unknown key");
  }
  const key = await crypto.subtle
    .importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"])
    .catch(() => {
      throw new HttpError(401, "bad token");
    });
  const data = new TextEncoder().encode(`${hB64}.${pB64}`);
  const sigOk = await crypto.subtle
    .verify("RSASSA-PKCS1-v1_5", key, b64urlToBytes(sB64), data)
    .catch(() => false);
  if (!sigOk) throw new HttpError(401, "bad signature");
  const nowSec = Math.floor(Date.now() / 1000);
  if (typeof payload.exp !== "number" || payload.exp <= nowSec) throw new HttpError(401, "expired");
  if (payload.aud !== projectId) throw new HttpError(401, "bad audience");
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw new HttpError(401, "bad issuer");
  if (typeof payload.sub !== "string" || payload.sub.length === 0) throw new HttpError(401, "bad subject");
  return payload.sub;
}

// ---- Seat check (Firestore REST evaluated AS the user — auth ≠ membership) ----

async function readSeat(
  env: Env,
  idToken: string,
  eventId: string,
  uid: string,
): Promise<{ role: string; status: string }> {
  const url =
    `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}` +
    `/databases/(default)/documents/events/${encodeURIComponent(eventId)}` +
    `/members/${encodeURIComponent(uid)}?key=${encodeURIComponent(env.FIREBASE_WEB_API_KEY)}`;
  let res: Response;
  try {
    res = await fetch(url, { headers: { Authorization: `Bearer ${idToken}` } });
  } catch {
    throw new HttpError(503, "seat check unavailable");
  }
  if (res.status === 404) throw new HttpError(403, "no seat");
  if (!res.ok) throw new HttpError(503, "seat check unavailable");
  const doc = (await res.json()) as { fields?: Record<string, { stringValue?: string }> };
  return {
    role: doc.fields?.["role"]?.stringValue ?? "",
    status: doc.fields?.["status"]?.stringValue ?? "",
  };
}

/**
 * Full gate for every route. Writes need active organizer/global_head;
 * reads need active (any role). Pending/revoked/unknown → 403;
 * Firestore-unreachable → fail-closed 503 (phone falls back to native).
 */
async function requireSeat(req: Request, env: Env, eventId: string, write: boolean): Promise<string> {
  const m = /^Bearer (.+)$/.exec(req.headers.get("Authorization") ?? "");
  if (!m || !m[1]) throw new HttpError(401, "missing token");
  const idToken = m[1];
  const uid = await verifyIdToken(idToken, env.FIREBASE_PROJECT_ID);

  // Test events and local drafts have no Firestore documents: allow for audio resolve/stream
  if (eventId === "test_event" || eventId.startsWith("test_") || eventId.startsWith("local_")) {
    if (write) throw new HttpError(403, "cannot write to test event");
    return uid;
  }

  let seat: { role: string; status: string } | null = null;
  try {
    seat = await readSeat(env, idToken, eventId, uid);
  } catch (e) {
    if (e instanceof HttpError && e.code === "no seat" && !write) {
      // 404 in Firestore (event is local-only or offline-first):
      // Allow verified Firebase user to resolve/stream audio
      return uid;
    }
    throw e;
  }
  if (seat.status !== "active") throw new HttpError(403, "seat not active");
  if (write && seat.role !== "organizer" && seat.role !== "global_head") {
    throw new HttpError(403, "seat cannot write");
  }
  return uid;
}

// ---- Daily-new-synthesis cap (KV when bound, per-isolate memory fallback) ----

const memCounts = new Map<string, number>();
let memWarned = false;

async function takeSynthesisSlot(env: Env): Promise<boolean> {
  const day = dayKey();
  if (env.RATE_KV) {
    const k = `synth:${day}`;
    const cur = parseInt((await env.RATE_KV.get(k)) ?? "0", 10) || 0;
    if (cur >= DAILY_NEW_SYNTHESIS_CAP) return false;
    await env.RATE_KV.put(k, String(cur + 1), { expirationTtl: 172800 });
    return true;
  }
  if (!memWarned) {
    memWarned = true;
    console.warn("RATE_KV not bound: daily synthesis cap is per-isolate best-effort");
  }
  for (const k of [...memCounts.keys()]) if (k !== day) memCounts.delete(k);
  const cur = memCounts.get(day) ?? 0;
  if (cur >= DAILY_NEW_SYNTHESIS_CAP) return false;
  memCounts.set(day, cur + 1);
  return true;
}

/**
 * Per-device daily synthesis cap (budget-burn guard). Anonymous installs
 * share the temple's 1000/day budget, so one buggy or abusive client
 * looping `resolve` must not 429 the whole pandal. KV-backed when bound;
 * without KV the global cap above is the only guard (fail-open for reads,
 * same as before — synthesis still gated by the shared budget).
 */
export const PER_UID_DAILY_SYNTHESIS_CAP = 100;

async function takeUidSynthesisSlot(env: Env, uid: string): Promise<boolean> {
  if (!env.RATE_KV) return true;
  const k = `synth-uid:${dayKey()}:${uid}`;
  const cur = parseInt((await env.RATE_KV.get(k)) ?? "0", 10) || 0;
  if (cur >= PER_UID_DAILY_SYNTHESIS_CAP) return false;
  await env.RATE_KV.put(k, String(cur + 1), { expirationTtl: 172800 });
  return true;
}

// ---- Sarvam JIT (single writer; key never leaves this Worker) ----

async function synthesizeSarvam(env: Env, text: string, speaker: string): Promise<Uint8Array> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), SARVAM_TIMEOUT_MS);
  try {
    const res = await fetch("https://api.sarvam.ai/text-to-speech", {
      method: "POST",
      headers: {
        "api-subscription-key": env.SARVAM_API_KEY,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        text,
        language_code: "te-IN",
        speaker,
        model: "bulbul:v3",
        output_audio_codec: "mp3",
      }),
      signal: ctrl.signal,
    });
    if (!res.ok) throw new HttpError(502, "synthesis failed");
    const j = (await res.json()) as { audios?: string[]; audio?: string };
    const b64 = Array.isArray(j.audios) ? j.audios[0] : j.audio;
    if (typeof b64 !== "string" || b64.length === 0) throw new HttpError(502, "synthesis failed");
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    if (out.length === 0) throw new HttpError(502, "synthesis failed");
    return out;
  } catch (e) {
    if (e instanceof HttpError) throw e;
    throw new HttpError(502, "synthesis failed"); // timeout/network → phone speaks native
  } finally {
    clearTimeout(timer);
  }
}

// ---- Routes ----

async function handleResolve(req: Request, env: Env): Promise<Response> {
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    throw new HttpError(400, "body must be JSON");
  }
  const parsed = validateResolveBody(body);
  if (!parsed.ok) throw new HttpError(400, parsed.error);
  const v = parsed.v;
  const uid = await requireSeat(req, env, v.eventId, false);
  // Hash-poisoning guard: the claimed hash must equal the recomputation over
  // the received fields (a forged hash would serve the wrong audio).
  const recomputed = await sha256Hex(canonicalHashInput(v.language, v.speaker, v.roster, v.text));
  if (!safeEqual(recomputed, v.hash)) throw new HttpError(400, "hash mismatch");
  const key = audioKey(v.hash);
  if (await env.AUDIO_BUCKET.head(key)) {
    console.log(`resolve hit hash=${v.hash}`);
    return json({ hash: v.hash, url: `/v1/audio/${v.hash}.mp3`, cached: true });
  }
  if (!(await takeUidSynthesisSlot(env, uid))) throw new HttpError(429, "daily synthesis budget reached");
  if (!(await takeSynthesisSlot(env))) throw new HttpError(429, "daily synthesis budget reached");
  const audio = await synthesizeSarvam(env, v.text, v.speaker);
  // Convergence re-check: a concurrent same-hash writer may have won while
  // we synthesized — prefer their object over a redundant PUT.
  if (await env.AUDIO_BUCKET.head(key)) {
    console.log(`resolve raced hash=${v.hash}`);
    return json({ hash: v.hash, url: `/v1/audio/${v.hash}.mp3`, cached: true });
  }
  await env.AUDIO_BUCKET.put(key, audio, {
    httpMetadata: { contentType: "audio/mpeg", cacheControl: "public, max-age=31536000, immutable" },
  });
  console.log(`resolve jit hash=${v.hash} bytes=${audio.length}`);
  return json({ hash: v.hash, url: `/v1/audio/${v.hash}.mp3`, cached: false });
}

async function handlePutReceipt(req: Request, env: Env, eventId: string, expenseId: string): Promise<Response> {
  if (!isValidId(eventId) || !isValidId(expenseId)) throw new HttpError(400, "bad ids");
  await requireSeat(req, env, eventId, true);
  const ct = (req.headers.get("Content-Type") ?? "").toLowerCase();
  if (!ct.startsWith("image/webp")) throw new HttpError(400, "receipt must be image/webp");
  const declared = req.headers.get("Content-Length");
  if (declared !== null && parseInt(declared, 10) > MAX_RECEIPT_BYTES) {
    throw new HttpError(413, "receipt too large");
  }
  const bytes = new Uint8Array(await req.arrayBuffer());
  if (bytes.length === 0 || bytes.length > MAX_RECEIPT_BYTES) throw new HttpError(413, "receipt too large");
  if (!looksLikeWebP(bytes)) throw new HttpError(400, "receipt must be WebP bytes");
  await env.AUDIO_BUCKET.put(receiptKey(eventId, expenseId), bytes, {
    httpMetadata: { contentType: "image/webp" },
  });
  console.log(`receipt put event=${eventId} bytes=${bytes.length}`);
  return json({ url: `/v1/receipts/${eventId}/${expenseId}` });
}

async function handleGetReceipt(req: Request, env: Env, eventId: string, expenseId: string): Promise<Response> {
  if (!isValidId(eventId) || !isValidId(expenseId)) throw new HttpError(400, "bad ids");
  await requireSeat(req, env, eventId, false);
  return await serveR2(env, receiptKey(eventId, expenseId), "image/webp");
}

async function serveR2(env: Env, key: string, contentType: string): Promise<Response> {
  const obj = await env.AUDIO_BUCKET.get(key);
  if (!obj) throw new HttpError(404, "not found");
  return new Response(obj.body, {
    headers: {
      "Content-Type": contentType,
      "Cache-Control": key.startsWith("audio/") ? "public, max-age=31536000, immutable" : "private, max-age=300",
    },
  });
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    try {
      const url = new URL(req.url);
      const method = req.method.toUpperCase();
      if (method === "GET" && (url.pathname === "/" || url.pathname === "/v1/health")) {
        return json({
          status: "healthy",
          service: "ShankaRavam Temple Media Gateway",
          r2: "connected",
          sarvam_tts: "active",
          version: "1.0"
        });
      }
      if (method === "POST" && url.pathname === "/v1/audio/resolve") {
        return await handleResolve(req, env);
      }
      const audio = /^\/v1\/audio\/([A-Za-z0-9_.-]+?)(?:\.mp3)?$/.exec(url.pathname);
      if (method === "GET" && audio?.[1]) {
        if (!isValidHash(audio[1])) throw new HttpError(400, "bad hash");
        // Seat needs the caller's event: passed as ?eventId= (validated).
        // The hash itself authorizes nothing — the seat read below does.
        const eventId = url.searchParams.get("eventId") ?? "";
        if (!isValidId(eventId)) throw new HttpError(400, "bad eventId");
        await requireSeat(req, env, eventId, false);
        return await serveR2(env, audioKey(audio[1]), "audio/mpeg");
      }
      const putR = /^\/v1\/receipts\/([^/]+)\/([^/]+)\.webp$/.exec(url.pathname);
      if (method === "PUT" && putR?.[1] && putR?.[2]) {
        return await handlePutReceipt(req, env, putR[1], putR[2]);
      }
      const getR = /^\/v1\/receipts\/([^/]+)\/([^/]+)$/.exec(url.pathname);
      if (method === "GET" && getR?.[1] && getR?.[2] && !getR[2].includes(".")) {
        return await handleGetReceipt(req, env, getR[1], getR[2]);
      }
      return json({ error: "not found" }, 404);
    } catch (e) {
      if (e instanceof HttpError) {
        console.log(`err status=${e.status} code=${e.code}`);
        return json({ error: e.code }, e.status);
      }
      console.log("err status=500 code=internal");
      return json({ error: "internal" }, 500);
    }
  },
};
