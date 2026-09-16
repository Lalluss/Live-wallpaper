import { put, list, del } from "@vercel/blob";
import crypto from "crypto";

// ==========================================
// ADMIN SESSION VERIFICATION
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

  // Session expires after 24 hours
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
    signature.length !==
    expectedSignature.length
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
// ADMIN CHECK
// ==========================================

function requireAdmin(req, res) {
  if (!verifyAdminSession(req)) {
    res.status(401).json({
      error: "Admin login required"
    });

    return false;
  }

  return true;
}


// ==========================================
// GET METADATA PATH FROM URL
// ==========================================

function getBlobPath(blobUrl) {
  try {
    const url = new URL(blobUrl);

    return decodeURIComponent(
      url.pathname.replace(/^\/+/, "")
    );
  } catch (error) {
    return null;
  }
}


// ==========================================
// DETERMINE MEDIA TYPE
// ==========================================

function normalizeType(type, mediaType, image) {
  const rawType = String(
    type || mediaType || ""
  ).toLowerCase();

  if (
    rawType === "live" ||
    rawType === "livewall" ||
    rawType === "live_wall" ||
    rawType === "video"
  ) {
    return "live";
  }

  const rawUrl = String(
    image || ""
  ).toLowerCase();

  if (
    /\.(mp4|webm|mov|m4v)(\?|$)/i.test(rawUrl)
  ) {
    return "live";
  }

  return "wall";
}


// ==========================================
// GET → LOAD ALL WALLPAPERS
// ==========================================

export default async function handler(req, res) {

  if (req.method === "GET") {

    try {

      const result = await list({
        prefix: "wallpapers-data/"
      });

      const wallpapers = [];

      for (const blob of result.blobs) {

        try {

          const response =
            await fetch(blob.url);

          const data =
            await response.json();

          // --------------------------------------
          // BACKWARD COMPATIBILITY
          // Old wallpapers without type
          // automatically become normal WALL
          // --------------------------------------

          const type = normalizeType(
            data.type,
            data.mediaType,
            data.image
          );

          wallpapers.push({

            ...data,

            type,

            mediaType:
              type === "live"
                ? "video"
                : "image",

            metadataUrl:
              blob.url

          });

        } catch (error) {

          console.error(
            "Metadata read error:",
            error
          );

        }
      }


      wallpapers.sort(
        (a, b) =>
          (b.createdAt || 0) -
          (a.createdAt || 0)
      );


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



  // ==========================================
  // POST → UPLOAD WALL / LIVE WALL
  // ==========================================

  if (req.method === "POST") {

    if (!requireAdmin(req, res)) {
      return;
    }


    try {

      const body = req.body;


      if (
        !body ||
        !body.title ||
        !body.image
      ) {

        return res.status(400).json({
          error:
            "Title and media are required"
        });

      }


      // --------------------------------------
      // DETERMINE TYPE
      // --------------------------------------

      const type = normalizeType(
        body.type,
        body.mediaType,
        body.image
      );

      const mediaType =
        type === "live"
          ? "video"
          : "image";


      // --------------------------------------
      // DATA URL VALIDATION
      // --------------------------------------

      const imageParts =
        body.image.split(",");


      if (imageParts.length < 2) {

        return res.status(400).json({
          error:
            "Invalid media data"
        });

      }


      // --------------------------------------
      // BASE64 → BUFFER
      // --------------------------------------

      const file =
        Buffer.from(
          imageParts[1],
          "base64"
        );


      // --------------------------------------
      // SAFE TITLE
      // --------------------------------------

      const safeTitle =
        body.title
          .replace(/[^a-z0-9]/gi, "-")
          .toLowerCase();


      const timestamp =
        Date.now();


      // --------------------------------------
      // FILE EXTENSION
      // --------------------------------------

      let extension = "jpg";

      if (type === "live") {

        if (
          body.image.includes("video/webm")
        ) {
          extension = "webm";

        } else if (
          body.image.includes("video/quicktime")
        ) {
          extension = "mov";

        } else if (
          body.image.includes("video/x-m4v")
        ) {
          extension = "m4v";

        } else {
          extension = "mp4";
        }

      }


      // --------------------------------------
      // CONTENT TYPE
      // --------------------------------------

      let contentType =
        "image/jpeg";

      if (type === "live") {

        if (extension === "webm") {
          contentType = "video/webm";

        } else if (extension === "mov") {
          contentType = "video/quicktime";

        } else if (extension === "m4v") {
          contentType = "video/x-m4v";

        } else {
          contentType = "video/mp4";
        }

      }


      // --------------------------------------
      // UPLOAD MEDIA TO VERCEL BLOB
      // --------------------------------------

      const mediaBlob =
        await put(
          `wallpapers/${timestamp}-${safeTitle}.${extension}`,
          file,
          {
            access: "public",
            addRandomSuffix: true,
            contentType
          }
        );


      // --------------------------------------
      // WALLPAPER METADATA
      // --------------------------------------

      const wallpaperData = {

        id:
          String(timestamp),

        title:
          body.title,

        image:
          mediaBlob.url,

        type,

        mediaType,

        createdAt:
          timestamp

      };


      // --------------------------------------
      // SAVE METADATA
      // --------------------------------------

      const metadataBlob =
        await put(
          `wallpapers-data/${timestamp}-${safeTitle}.json`,
          JSON.stringify(
            wallpaperData
          ),
          {
            access: "public",
            addRandomSuffix: true,
            allowOverwrite: false,
            contentType:
              "application/json"
          }
        );


      return res.status(200).json({

        success: true,

        wallpaper: {

          ...wallpaperData,

          metadataUrl:
            metadataBlob.url

        }

      });


    } catch (error) {

      console.error(
        "Wallpaper upload error:",
        error
      );


      return res.status(500).json({

        error:
          error?.message ||
          "Wallpaper upload failed"

      });

    }
  }



  // ==========================================
  // PUT → EDIT WALLPAPER
  // ==========================================

  if (req.method === "PUT") {

    if (!requireAdmin(req, res)) {
      return;
    }


    try {

      const body = req.body;


      if (
        !body ||
        !body.metadataUrl ||
        !body.title
      ) {

        return res.status(400).json({

          error:
            "Metadata URL and title are required"

        });

      }


      // --------------------------------------
      // READ EXISTING METADATA
      // --------------------------------------

      const response =
        await fetch(
          body.metadataUrl
        );


      if (!response.ok) {

        return res.status(404).json({

          error:
            "Wallpaper metadata not found"

        });

      }


      const oldData =
        await response.json();


      // --------------------------------------
      // KEEP EXISTING MEDIA
      // --------------------------------------

      let newImageUrl =
        oldData.image;


      let newType =
        normalizeType(
          oldData.type,
          oldData.mediaType,
          oldData.image
        );


      let newMediaType =
        newType === "live"
          ? "video"
          : "image";


      // --------------------------------------
      // OPTIONAL MEDIA REPLACEMENT
      // --------------------------------------

      if (body.image) {

        const imageParts =
          body.image.split(",");


        if (imageParts.length < 2) {

          return res.status(400).json({

            error:
              "Invalid media data"

          });

        }


        const file =
          Buffer.from(
            imageParts[1],
            "base64"
          );


        // Determine replacement type
        newType =
          normalizeType(
            body.type,
            body.mediaType,
            body.image
          );


        newMediaType =
          newType === "live"
            ? "video"
            : "image";


        const safeTitle =
          body.title
            .replace(
              /[^a-z0-9]/gi,
              "-"
            )
            .toLowerCase();


        let extension = "jpg";

        let contentType =
          "image/jpeg";


        if (newType === "live") {

          if (
            body.image.includes(
              "video/webm"
            )
          ) {

            extension = "webm";
            contentType = "video/webm";

          } else if (
            body.image.includes(
              "video/quicktime"
            )
          ) {

            extension = "mov";
            contentType =
              "video/quicktime";

          } else if (
            body.image.includes(
              "video/x-m4v"
            )
          ) {

            extension = "m4v";
            contentType =
              "video/x-m4v";

          } else {

            extension = "mp4";
            contentType = "video/mp4";

          }

        }


        // Upload replacement
        const mediaBlob =
          await put(
            `wallpapers/${Date.now()}-${safeTitle}.${extension}`,
            file,
            {
              access: "public",
              addRandomSuffix: true,
              contentType
            }
          );


        newImageUrl =
          mediaBlob.url;


        // Delete old media
        if (oldData.image) {

          try {

            await del(
              oldData.image
            );

          } catch (deleteError) {

            console.error(
              "Old media delete error:",
              deleteError
            );

          }

        }

      }


      // --------------------------------------
      // UPDATED DATA
      // --------------------------------------

      const updatedData = {

        ...oldData,

        title:
          body.title,

        image:
          newImageUrl,

        type:
          newType,

        mediaType:
          newMediaType,

        updatedAt:
          Date.now()

      };


      // --------------------------------------
      // GET EXISTING METADATA PATH
      // --------------------------------------

      const metadataPath =
        getBlobPath(
          body.metadataUrl
        );


      if (!metadataPath) {

        return res.status(400).json({

          error:
            "Invalid metadata URL"

        });

      }


      // --------------------------------------
      // OVERWRITE METADATA
      // --------------------------------------

      await put(
        metadataPath,
        JSON.stringify(
          updatedData
        ),
        {
          access: "public",
          addRandomSuffix: false,
          allowOverwrite: true,
          contentType:
            "application/json"
        }
      );


      return res.status(200).json({

        success: true,

        wallpaper: {

          ...updatedData,

          metadataUrl:
            body.metadataUrl

        }

      });


    } catch (error) {

      console.error(
        "Wallpaper edit error:",
        error
      );


      return res.status(500).json({

        error:
          error?.message ||
          "Wallpaper update failed"

      });

    }
  }



  // ==========================================
  // DELETE → DELETE WALL / LIVE WALL
  // ==========================================

  if (req.method === "DELETE") {

    if (!requireAdmin(req, res)) {
      return;
    }


    try {

      const body = req.body;


      if (
        !body ||
        !body.metadataUrl
      ) {

        return res.status(400).json({

          error:
            "Metadata URL is required"

        });

      }


      // --------------------------------------
      // READ METADATA
      // --------------------------------------

      const response =
        await fetch(
          body.metadataUrl
        );


      if (!response.ok) {

        return res.status(404).json({

          error:
            "Wallpaper not found"

        });

      }


      const data =
        await response.json();


      // --------------------------------------
      // DELETE METADATA
      // --------------------------------------

      try {

        await del(
          body.metadataUrl
        );

      } catch (metadataError) {

        console.error(
          "Metadata delete error:",
          metadataError
        );

        throw metadataError;

      }


      // --------------------------------------
      // DELETE MEDIA
      // Works for both image and video
      // --------------------------------------

      if (data.image) {

        try {

          await del(
            data.image
          );

        } catch (mediaError) {

          console.error(
            "Media delete error:",
            mediaError
          );

        }

      }


      return res.status(200).json({

        success: true,

        message:
          "Wallpaper deleted"

      });


    } catch (error) {

      console.error(
        "Wallpaper delete error:",
        error
      );


      return res.status(500).json({

        error:
          error?.message ||
          "Wallpaper delete failed"

      });

    }

  }



  // ==========================================
  // METHOD NOT ALLOWED
  // ==========================================

  return res.status(405).json({

    error:
      "Method not allowed"

  });

}
