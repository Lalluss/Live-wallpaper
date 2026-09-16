import { put, list, del } from "@vercel/blob";
import crypto from "crypto";

const DATA_PREFIX = "wallpapers-data/";
const MEDIA_PREFIX = "wallpapers/";

function getSecret() {
  return (
    process.env.SESSION_SECRET ||
    process.env.ADMIN_SECRET ||
    process.env.ADMIN_PASSWORD ||
    "change-this-secret"
  );
}

function getCookie(req, name) {
  const cookie = req.headers.cookie || "";

  const match = cookie.match(
    new RegExp(
      "(?:^|;\\s*)" +
        name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") +
        "=([^;]*)"
    )
  );

  return match ? decodeURIComponent(match[1]) : null;
}

function verifyAdmin(req) {
  const token = getCookie(req, "admin_session");

  if (!token) return false;

  try {
    const [timestamp, signature] = token.split(".");

    if (!timestamp || !signature) return false;

    const maxAge = 24 * 60 * 60 * 1000;

    if (Date.now() - Number(timestamp) > maxAge) {
      return false;
    }

    const expected = crypto
      .createHmac("sha256", getSecret())
      .update(timestamp)
      .digest("hex");

    return crypto.timingSafeEqual(
      Buffer.from(signature),
      Buffer.from(expected)
    );
  } catch {
    return false;
  }
}

function isBlobUrl(value) {
  return (
    typeof value === "string" &&
    /^https:\/\/[^/]+\.blob\.vercel-storage\.com\//i.test(value)
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

async function getAllWallpapers() {
  const result = await list({
    prefix: DATA_PREFIX
  });

  const wallpapers = [];

  for (const blob of result.blobs) {
    try {
      const response = await fetch(blob.url);
      const data = await response.json();

      if (!data || !data.id) continue;

      wallpapers.push({
        ...data,

        // Old files compatibility
        type: data.type || "wall",
        mediaType:
          data.mediaType ||
          detectMediaType(data.image)
      });
    } catch (error) {
      console.error(
        "Failed reading metadata:",
        blob.pathname,
        error
      );
    }
  }

  return wallpapers;
}

async function saveMetadata(data) {
  const id = data.id;

  const metadataPath = `${DATA_PREFIX}${id}.json`;

  const blob = await put(
    metadataPath,
    JSON.stringify(data),
    {
      access: "public",
      addRandomSuffix: false,
      contentType: "application/json"
    }
  );

  return blob;
}

async function findMetadata(id) {
  const result = await list({
    prefix: `${DATA_PREFIX}${id}.json`
  });

  if (!result.blobs.length) {
    return null;
  }

  const blob = result.blobs[0];

  try {
    const response = await fetch(blob.url);
    return await response.json();
  } catch {
    return null;
  }
}

async function deleteMetadata(id) {
  const result = await list({
    prefix: `${DATA_PREFIX}${id}.json`
  });

  if (result.blobs.length) {
    await del(result.blobs[0].url);
  }
}

async function deleteMedia(mediaUrl) {
  if (!mediaUrl || !isBlobUrl(mediaUrl)) {
    return;
  }

  try {
    await del(mediaUrl);
  } catch (error) {
    console.error("Media delete failed:", error);
  }
}

function makeId() {
  return (
    Date.now().toString(36) +
    "-" +
    crypto.randomBytes(6).toString("hex")
  );
}

export default async function handler(req, res) {
  try {
    /*
     * =========================
     * GET
     * =========================
     */

    if (req.method === "GET") {
      const wallpapers = await getAllWallpapers();

      // Newest first
      wallpapers.sort((a, b) => {
        return Number(b.createdAt || 0) - Number(a.createdAt || 0);
      });

      return res.status(200).json({
        wallpapers
      });
    }

    /*
     * =========================
     * ADMIN CHECK
     * =========================
     */

    if (!verifyAdmin(req)) {
      return res.status(401).json({
        error: "Unauthorized"
      });
    }

    /*
     * =========================
     * POST
     * Create new wallpaper
     * =========================
     */

    if (req.method === "POST") {
      const {
        title,
        image,
        type,
        mediaType
      } = req.body || {};

      if (!title || !image) {
        return res.status(400).json({
          error: "Title and image are required"
        });
      }

      const wallpaperType =
        type === "live" ? "live" : "wall";

     
