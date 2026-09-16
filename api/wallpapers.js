import { put, list, del } from "@vercel/blob";
import crypto from "crypto";

// ==========================================
// CONFIG
// ==========================================

const DATA_PREFIX = "wallpapers-data/";
const MEDIA_PREFIX = "wallpapers/";

// ==========================================
// ADMIN SESSION
// ==========================================

function verifyAdminSession(req) {
  const cookies = req.headers.cookie || "";

  const match = cookies.match(
    /(?:^|;\s*)admin_session=([^;]+)/
  );

  if (!match) {
    return false;
  }

  const token = match[1];

  const parts = token.split(".");

  if (parts.length !== 2) {
    return false;
  }

  const timestamp = parts[0];
  const signature = parts[1];

  const timestampNumber = Number(timestamp);

  if (!Number.isFinite(timestampNumber)) {
    return false;
  }

  // 24 hour expiry
  if (
    Date.now() - timestampNumber > 86400000 ||
    timestampNumber > Date.now()
  ) {
    return false;
  }

  const expectedSignature = crypto
    .createHmac(
      "sha256",
      process.env.ADMIN_PASSWORD
    )
    .update(timestamp)
    .digest("hex");

  if (
    signature.length !== expectedSignature.length
  ) {
    return false;
  }

  try {
    return crypto.timingSafeEqual(
      Buffer.from(signature),
      Buffer.from(expectedSignature)
    );
  } catch (error) {
    console.error(
      "Session verification error:",
      error
    );

    return false;
  }
}

// ==========================================
// HELPERS
// ==========================================

function isBlobUrl(value) {
  return (
    typeof value === "string" &&
    /^https:\/\/[^/]+\.blob\.vercel-storage\.com\//i.test(
      value
    )
  );
}

function isDataUrl(value) {
  return (
    typeof value === "string" &&
    /^data:[^;]+;base64,/i.test(value)
  );
}

function detectMediaType(value) {
  if (!value || typeof value !== "string") {
    return "image";
  }

  if (
    value.startsWith("data:video/") ||
    /\.(mp4|webm|mov|m4v)(\?|$)/i.test(value)
  ) {
    return "video";
  }

  return "image";
}

function makeId() {
  return (
    Date.now().toString(36) +
    "-" +
    crypto.randomBytes(6).toString("hex")
  );
}

// ==========================================
// FIND METADATA
// ==========================================

async function find
