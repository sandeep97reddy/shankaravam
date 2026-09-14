import { describe, expect, it } from "vitest";
import {
  audioKey,
  canonicalHashInput,
  dayKey,
  isValidHash,
  isValidId,
  looksLikeWebP,
  normalizeSarvamSpeaker,
  receiptKey,
  safeEqual,
  sha256Hex,
  validateResolveBody,
} from "../src/lib";

describe("normalizeSarvamSpeaker parity (Android SarvamTtsClient.kt)", () => {
  it("passes known speakers through", () => {
    expect(normalizeSarvamSpeaker("shubh")).toBe("shubh");
    expect(normalizeSarvamSpeaker("pooja")).toBe("pooja");
    expect(normalizeSarvamSpeaker("priya")).toBe("priya");
    expect(normalizeSarvamSpeaker("kavitha")).toBe("kavitha");
    expect(normalizeSarvamSpeaker("ratan")).toBe("ratan");
  });
  it("maps legacy aliases", () => {
    expect(normalizeSarvamSpeaker("meera")).toBe("kavitha");
    expect(normalizeSarvamSpeaker("arvind")).toBe("aditya");
  });
  it("is case/whitespace tolerant", () => {
    expect(normalizeSarvamSpeaker(" SHUBH ")).toBe("shubh");
    expect(normalizeSarvamSpeaker("Pooja")).toBe("pooja");
  });
  it("corrupt values land on shubh (T0.5)", () => {
    expect(normalizeSarvamSpeaker(null)).toBe("shubh");
    expect(normalizeSarvamSpeaker(undefined)).toBe("shubh");
    expect(normalizeSarvamSpeaker("")).toBe("shubh");
    expect(normalizeSarvamSpeaker("   ")).toBe("shubh");
    expect(normalizeSarvamSpeaker("not-a-voice")).toBe("shubh");
    expect(normalizeSarvamSpeaker(42)).toBe("shubh");
  });
});

describe("id + hash validation", () => {
  it("accepts UUID-shaped ids, rejects traversal", () => {
    expect(isValidId("550e8400-e29b-41d4-a716-446655440000")).toBe(true);
    expect(isValidId("abc_123-XYZ")).toBe(true);
    expect(isValidId("")).toBe(false);
    expect(isValidId("../secret")).toBe(false);
    expect(isValidId("a/b")).toBe(false);
    expect(isValidId("a".repeat(65))).toBe(false);
    expect(isValidId(null)).toBe(false);
  });
  it("accepts only 64-hex hashes", () => {
    expect(isValidHash("a".repeat(64))).toBe(true);
    expect(isValidHash("A".repeat(64))).toBe(false);
    expect(isValidHash("abc")).toBe(false);
  });
});

describe("canonical hash (byte-twin of Kotlin audioHashFor)", () => {
  it("matches the pinned cross-language vector", async () => {
    // SHA-256 of the exact bytes "TELUGU|shubh|false|hello" — the Kotlin
    // AudioImportTest pins the same vector. Both must stay identical.
    expect(await sha256Hex(canonicalHashInput("TELUGU", "shubh", false, "hello"))).toBe(
      "27b47756fd423f48871ac61ae97d6e2e6457a5852535da434528308f94d4eb1d",
    );
  });
  it("any byte change forks the hash", async () => {
    const base = await sha256Hex(canonicalHashInput("TELUGU", "shubh", false, "hello"));
    expect(await sha256Hex(canonicalHashInput("TELUGU", "shubh", false, "hello!"))).not.toBe(base);
    expect(await sha256Hex(canonicalHashInput("ENGLISH", "shubh", false, "hello"))).not.toBe(base);
    expect(await sha256Hex(canonicalHashInput("TELUGU", "priya", false, "hello"))).not.toBe(base);
    expect(await sha256Hex(canonicalHashInput("TELUGU", "shubh", true, "hello"))).not.toBe(base);
  });
  it("safeEqual rejects length mismatch and timing-naive compare", () => {
    expect(safeEqual("abc", "abc")).toBe(true);
    expect(safeEqual("abc", "abd")).toBe(false);
    expect(safeEqual("abc", "abcd")).toBe(false);
  });
});

describe("validateResolveBody (400 matrix)", () => {
  const good = {
    eventId: "evt-1",
    donationId: "don-1",
    text: "hello",
    language: "TELUGU",
    speaker: "shubh",
    roster: false,
    hash: "a".repeat(64),
    templateVersion: "v1",
  };
  it("accepts a well-formed body and normalizes the speaker", () => {
    const r = validateResolveBody({ ...good, speaker: " SHUBH " });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.v.speaker).toBe("shubh");
  });
  it("rejects non-objects, bad ids, bad enums, oversize text", () => {
    expect(validateResolveBody(null).ok).toBe(false);
    expect(validateResolveBody({ ...good, eventId: "../x" }).ok).toBe(false);
    expect(validateResolveBody({ ...good, donationId: "" }).ok).toBe(false);
    expect(validateResolveBody({ ...good, text: "" }).ok).toBe(false);
    expect(validateResolveBody({ ...good, text: "x".repeat(501) }).ok).toBe(false);
    expect(validateResolveBody({ ...good, language: "HINDI" }).ok).toBe(false);
    expect(validateResolveBody({ ...good, roster: "false" }).ok).toBe(false);
    expect(validateResolveBody({ ...good, speaker: "" }).ok).toBe(false);
    expect(validateResolveBody({ ...good, templateVersion: "" }).ok).toBe(false);
    expect(validateResolveBody({ ...good, hash: "xyz" }).ok).toBe(false);
  });
  it("accepts exactly-500-char text", () => {
    expect(validateResolveBody({ ...good, text: "x".repeat(500) }).ok).toBe(true);
  });
});

describe("receipt helpers", () => {
  it("accepts RIFF....WEBP magic only", () => {
    const webp = new Uint8Array([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 1]);
    expect(looksLikeWebP(webp)).toBe(true);
    expect(looksLikeWebP(new Uint8Array([1, 2, 3]))).toBe(false);
    const png = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
    expect(looksLikeWebP(png)).toBe(false);
  });
  it("keys are namespaced, never listable from phones", () => {
    expect(audioKey("a".repeat(64))).toBe(`audio/${"a".repeat(64)}.mp3`);
    expect(receiptKey("e1", "x1")).toBe("receipts/e1/x1.webp");
  });
  it("dayKey buckets UTC days", () => {
    expect(dayKey(Date.parse("2026-09-14T00:00:00Z"))).toBe("2026-09-14");
  });
});
