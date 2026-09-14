/**
 * Pure gateway logic (no fetch/R2/KV): speaker parity, validation, hashing.
 * Fully covered by vitest — the security-critical comparisons live here, not
 * in the route handlers.
 */

// Guard-rails (R2_IMPLEMENTATION_PLAN §7). TEXT cap measured in
// app/.../MaxTemplateLengthTest (worst realistic bilingual < 500).
export const MAX_TEXT_LENGTH = 500;
export const MAX_RECEIPT_BYTES = 150 * 1024;
export const DAILY_NEW_SYNTHESIS_CAP = 1000;
export const SARVAM_TIMEOUT_MS = 8000;
export const LANGUAGES = ["TELUGU", "ENGLISH", "BILINGUAL"] as const;

/**
 * Speaker-normalization parity with Android
 * `normalizeSarvamSpeaker` (SarvamTtsClient.kt). THE ONLY Telugu-domain logic
 * ported to TypeScript — canonical text is built phone-side. Pinned by
 * vitest vectors (including corrupt → shubh).
 */
const SPEAKER_MAP: Record<string, string> = {
  priya: "priya",
  shubh: "shubh",
  kavitha: "kavitha",
  meera: "kavitha",
  aditya: "aditya",
  arvind: "aditya",
  ratan: "ratan",
  neha: "neha",
  ishita: "ishita",
  mani: "mani",
  vijay: "vijay",
  ritu: "ritu",
  roopa: "roopa",
  suhani: "suhani",
  pooja: "pooja",
  ashutosh: "ashutosh",
  rehan: "rehan",
  rohan: "rohan",
  varun: "varun",
};

export function normalizeSarvamSpeaker(raw: unknown): string {
  const k = typeof raw === "string" ? raw.toLowerCase().trim() : "";
  return SPEAKER_MAP[k] ?? "shubh";
}

/** Record ids are app UUIDs; accept the UUID alphabet, reject traversal. */
export function isValidId(id: unknown): id is string {
  return typeof id === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(id);
}

export function isValidHash(h: unknown): h is string {
  return typeof h === "string" && /^[0-9a-f]{64}$/.test(h);
}

export async function sha256Hex(s: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Byte-exact twin of Kotlin `audioHashFor`: SHA-256 hex of
 * `"language|speaker|roster|text"`. `roster` stringifies to "true"/"false"
 * in both languages; language is the UPPER enum name; speaker is the
 * NORMALIZED id; text is the client-built canonical bytes, verbatim.
 */
export function canonicalHashInput(language: string, speaker: string, roster: boolean, text: string): string {
  return `${language}|${speaker}|${roster}|${text}`;
}

/** Length-mismatch-safe compare for client-claimed hashes (poisoning guard). */
export function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export interface ResolveBody {
  eventId: string;
  donationId: string;
  text: string;
  language: string;
  speaker: string; // normalized
  roster: boolean;
  hash: string;
  templateVersion: string; // informational (text bytes carry template identity)
}

type Invalid = { ok: false; error: string };

/**
 * POST-only structured fields (never free text in query — donor PII stays
 * out of edge logs). Every rejection is a 400 with a non-PII reason.
 */
export function validateResolveBody(b: unknown): { ok: true; v: ResolveBody } | Invalid {
  if (typeof b !== "object" || b === null) return { ok: false, error: "body must be JSON" };
  const o = b as Record<string, unknown>;
  if (!isValidId(o["eventId"])) return { ok: false, error: "bad eventId" };
  if (!isValidId(o["donationId"])) return { ok: false, error: "bad donationId" };
  if (typeof o["text"] !== "string" || o["text"].length === 0) return { ok: false, error: "bad text" };
  if (o["text"].length > MAX_TEXT_LENGTH) return { ok: false, error: "text too long" };
  if (typeof o["language"] !== "string" || !(LANGUAGES as readonly string[]).includes(o["language"])) {
    return { ok: false, error: "bad language" };
  }
  if (typeof o["roster"] !== "boolean") return { ok: false, error: "bad roster" };
  if (typeof o["speaker"] !== "string" || o["speaker"].length === 0 || o["speaker"].length > 32) {
    return { ok: false, error: "bad speaker" };
  }
  if (typeof o["templateVersion"] !== "string" || o["templateVersion"].length === 0 || o["templateVersion"].length > 16) {
    return { ok: false, error: "bad templateVersion" };
  }
  if (!isValidHash(o["hash"])) return { ok: false, error: "bad hash" };
  return {
    ok: true,
    v: {
      eventId: o["eventId"] as string,
      donationId: o["donationId"] as string,
      text: o["text"] as string,
      language: o["language"] as string,
      speaker: normalizeSarvamSpeaker(o["speaker"]),
      roster: o["roster"] as boolean,
      hash: o["hash"] as string,
      templateVersion: o["templateVersion"] as string,
    },
  };
}

/** Minimal WebP check (RIFF....WEBP) — cheap abuse filter before R2 PUT. */
export function looksLikeWebP(bytes: Uint8Array): boolean {
  if (bytes.length < 12) return false;
  return (
    bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 &&
    bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50
  );
}

/** UTC date key for the daily synthesis counter. */
export function dayKey(now: number = Date.now()): string {
  return new Date(now).toISOString().slice(0, 10);
}

export function receiptKey(eventId: string, expenseId: string): string {
  return `receipts/${eventId}/${expenseId}.webp`;
}

export function audioKey(hash: string): string {
  return `audio/${hash}.mp3`;
}
