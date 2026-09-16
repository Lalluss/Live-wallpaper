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

async function findMetadata(id) {
  const result = await list({
    prefix: `${DATA_PREFIX}${id}.json`
  });

  if (!result.blobs.length) {
    return null;
  }

  try {
    const response = await fetch(
      result.blobs[0].url
    );

    return await response.json();
  } catch (error) {
    console.error(
      "Metadata fetch error:",
      error
    );

    return null;
  }
}

// ==========================================
// SAVE METADATA
// ==========================================

async function saveMetadata(data) {
  const path =
    `${DATA_PREFIX}${data.id}.json`;

  return await put(
    path,
    JSON.stringify(data),
    {
      access: "public",
      addRandomSuffix: false,
      contentType: "application/json"
    }
  );
}

// ==========================================
// DELETE MEDIA
// ==========================================

async function deleteMedia(url) {
  if (!url || !isBlobUrl(url)) {
    return;
  }

  try {
    await del(url);
  } catch (error) {
    console.error(
      "Media delete error:",
      error
    );
  }
}

// ==========================================
// DELETE METADATA
// ==========================================

async function deleteMetadata(id) {
  try {
    const result = await list({
      prefix: `${DATA_PREFIX}${id}.json`
    });

    for (const blob of result.blobs) {
      await del(blob.url);
    }
  } catch (error) {
    console.error(
      "Metadata delete error:",
      error
    );
  }
}

// ==========================================
// GET ALL WALLPAPERS
// ==========================================

async function getAllWallpapers() {
  const result = await list({
    prefix: DATA_PREFIX
  });

  const wallpapers = [];

  for (const blob of result.blobs) {
    try {
      const response = await fetch(blob.url);

      const data = await response.json();

      if (!data) {
        continue;
      }

      // Old wallpapers compatibility
      if (!data.type) {
        data.type = "wall";
      }

      if (!data.mediaType) {
        data.mediaType =
          detectMediaType(data.image);
      }

      wallpapers.push(data);

    } catch (error) {
      console.error(
        "Metadata read error:",
        error
      );
    }
  }

  wallpapers.sort(
    (a, b) =>
      Number(b.createdAt || 0) -
      Number(a.createdAt || 0)
  );

  return wallpapers;
}

// ==========================================
// MAIN API
// ==========================================

export default async function handler(req, res) {

  // ========================================
  // GET
  // ========================================

  if (req.method === "GET") {

    try {

      const wallpapers =
        await getAllWallpapers();

      return res.status(200).json({
        wallpapers
      });

    } catch (error) {

      console.error(
        "GET wallpapers error:",
        error
      );

      return res.status(500).json({
        error:
          "Failed to load wallpapers"
      });
    }
  }

  // ========================================
  // ADMIN CHECK
  // ========================================

  if (!verifyAdminSession(req)) {

    return res.status(401).json({
      error:
        "Admin login required"
    });
  }

  // ========================================
  // POST
  // NEW WALLPAPER
  // ========================================

  if (req.method === "POST") {

    try {

      const body = req.body || {};

      const title = body.title;
      const image = body.image;

      const type =
        body.type === "live"
          ? "live"
          : "wall";

      const mediaType =
        body.mediaType ||
        detectMediaType(image);

      if (!title || !image) {

        return res.status(400).json({
          error:
            "Title and image are required"
        });
      }

      let finalUrl = image;

      // ======================================
      // NEW SYSTEM
      // Image/video already uploaded directly
      // to Vercel Blob
      // ======================================

      if (isBlobUrl(image)) {

        finalUrl = image;
      }

      // ======================================
      // OLD SYSTEM
      // Base64 compatibility
      // ======================================

      else if (isDataUrl(image)) {

        const match =
          image.match(
            /^data:([^;]+);base64,(.+)$/i
          );

        if (!match) {

          return res.status(400).json({
            error:
              "Invalid media data"
          });
        }

        const contentType = match[1];

        const buffer =
          Buffer.from(
            match[2],
            "base64"
          );

        let extension = "jpg";

        if (
          contentType ===
          "image/png"
        ) {
          extension = "png";
        }

        else if (
          contentType ===
          "image/webp"
        ) {
          extension = "webp";
        }

        else if (
          contentType ===
          "video/mp4"
        ) {
          extension = "mp4";
        }

        else if (
          contentType ===
          "video/webm"
        ) {
          extension = "webm";
        }

        else if (
          contentType ===
          "video/quicktime"
        ) {
          extension = "mov";
        }

        const mediaFolder =
          type === "live"
            ? `${MEDIA_PREFIX}live/`
            : MEDIA_PREFIX;

        const filename =
          `${mediaFolder}${Date.now()}-${crypto
            .randomBytes(6)
            .toString("hex")}.${extension}`;

        const blob =
          await put(
            filename,
            buffer,
            {
              access: "public",
              addRandomSuffix: true,
              contentType
            }
          );

        finalUrl = blob.url;
      }

      else {

        return res.status(400).json({
          error:
            "Invalid media URL"
        });
      }

      // ======================================
      // CREATE METADATA
      // ======================================

      const id = makeId();

      const wallpaperData = {

        id,

        title:
          String(title).trim(),

        image:
          finalUrl,

        // wall / live
        type,

        // image / video
        mediaType,

        createdAt:
          Date.now()
      };

      // ======================================
      // SAVE METADATA
      // ======================================

      await saveMetadata(
        wallpaperData
      );

      // ======================================
      // SUCCESS
      // ======================================

      return res.status(200).json({

        success: true,

        wallpaper:
          wallpaperData

      });

    } catch (error) {

      console.error(
        "POST wallpaper error:",
        error
      );

      return res.status(500).json({

        error:
          error?.message ||
          "Wallpaper upload failed"

      });
    }
  }

  // ========================================
  // PUT
  // EDIT WALLPAPER
  // ========================================

  if (req.method === "PUT") {

    try {

      const body = req.body || {};

      const id = body.id;

      if (!id) {

        return res.status(400).json({
          error:
            "Wallpaper ID is required"
        });
      }

      const oldData =
        await findMetadata(id);

      if (!oldData) {

        return res.status(404).json({
          error:
            "Wallpaper not found"
        });
      }

      const updated = {
        ...oldData
      };

      // ======================================
      // TITLE
      // ======================================

      if (
        body.title !== undefined
      ) {

        updated.title =
          String(
            body.title
          ).trim();
      }

      // ======================================
      // TYPE
      // ======================================

      if (
        body.type === "live" ||
        body.type === "wall"
      ) {

        updated.type =
          body.type;
      }

      // ======================================
      // MEDIA
      // ======================================

      if (body.image) {

        const newImage =
          body.image;

        // New Blob URL
        if (
          isBlobUrl(newImage)
        ) {

          if (
            oldData.image &&
            oldData.image !==
              newImage
          ) {

            await deleteMedia(
              oldData.image
            );
          }

          updated.image =
            newImage;

          updated.mediaType =
            body.mediaType ||
            detectMediaType(
              newImage
            );
        }

        // Old base64 support
        else if (
          isDataUrl(newImage)
        ) {

          const match =
            newImage.match(
              /^data:([^;]+);base64,(.+)$/i
            );

          if (!match) {

            return res.status(400).json({
              error:
                "Invalid media data"
            });
          }

          const contentType =
            match[1];

          const buffer =
            Buffer.from(
              match[2],
              "base64"
            );

          let extension = "jpg";

          if (
            contentType ===
            "video/mp4"
          ) {
            extension = "mp4";
          }

          else if (
            contentType ===
            "video/webm"
          ) {
            extension = "webm";
          }

          else if (
            contentType ===
            "image/png"
          ) {
            extension = "png";
          }

          else if (
            contentType ===
            "image/webp"
          ) {
            extension = "webp";
          }

          const folder =
            updated.type === "live"
              ? `${MEDIA_PREFIX}live/`
              : MEDIA_PREFIX;

          const filename =
            `${folder}${Date.now()}-${crypto
              .randomBytes(6)
              .toString("hex")}.${extension}`;

          const blob =
            await put(
              filename,
              buffer,
              {
                access: "public",
                addRandomSuffix: true,
                contentType
              }
            );

          if (
            oldData.image
          ) {
            await deleteMedia(
              oldData.image
            );
          }

          updated.image =
            blob.url;

          updated.mediaType =
            body.mediaType ||
            detectMediaType(
              blob.url
            );
        }

        else {

          return res.status(400).json({
            error:
              "Invalid media URL"
          });
        }
      }

      if (
        !updated.mediaType
      ) {

        updated.mediaType =
          detectMediaType(
            updated.image
          );
      }

      updated.updatedAt =
        Date.now();

      // ======================================
      // SAVE
      // ======================================

      await saveMetadata(
        updated
      );

      return res.status(200).json({

        success: true,

        wallpaper:
          updated

      });

    } catch (error) {

      console.error(
        "PUT wallpaper error:",
        error
      );

      return res.status(500).json({

        error:
          error?.message ||
          "Wallpaper update failed"

      });
    }
  }

  // ========================================
  // DELETE
  // ========================================

  if (req.method === "DELETE") {

    try {

      const id =
        req.query?.id ||
        req.body?.id;

      if (!id) {

        return res.status(400).json({
          error:
            "Wallpaper ID is required"
        });
      }

      const data =
        await findMetadata(id);

      if (!data) {

        return res.status(404).json({
          error:
            "Wallpaper not found"
        });
      }

      // Delete image/video
      if (data.image) {

        await deleteMedia(
          data.image
        );
      }

      // Delete metadata
      await deleteMetadata(id);

      return res.status(200).json({

        success: true

      });

    } catch (error) {

      console.error(
        "DELETE wallpaper error:",
        error
      );

      return res.status(500).json({

        error:
          error?.message ||
          "Wallpaper delete failed"

      });
    }
  }

  // ========================================
  // METHOD NOT ALLOWED
  // ========================================

  return res.status(405).json({

    error:
      "Method not allowed"

  });
}
