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
// MAIN API
// ==========================================

export default async function handler(req, res) {


  // ==========================================
  // GET → LOAD ALL WALLPAPERS
  // ==========================================

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

          wallpapers.push({

            ...data,

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
          b.createdAt - a.createdAt
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
  // POST → UPLOAD WALLPAPER
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
            "Title and image are required"
        });
      }


      // Convert base64 image
      const imageParts =
        body.image.split(",");


      if (imageParts.length < 2) {

        return res.status(400).json({
          error:
            "Invalid image data"
        });
      }


      const file = Buffer.from(
        imageParts[1],
        "base64"
      );


      // Safe filename
      const safeTitle =
        body.title
          .replace(/[^a-z0-9]/gi, "-")
          .toLowerCase();


      const timestamp =
        Date.now();


      // Upload image
      const imageBlob =
        await put(
          `wallpapers/${timestamp}-${safeTitle}.jpg`,
          file,
          {
            access: "public",
            addRandomSuffix: true
          }
        );


      // Wallpaper metadata
      const wallpaperData = {

        id:
          String(timestamp),

        title:
          body.title,

        image:
          imageBlob.url,

        createdAt:
          timestamp

      };


      // Save metadata
      const metadataBlob =
        await put(
          `wallpapers-data/${timestamp}-${safeTitle}.json`,
          JSON.stringify(
            wallpaperData
          ),
          {
            access: "public",
            addRandomSuffix: true,
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


      // Read existing metadata
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


      // ------------------------------------------
      // OPTIONAL: REPLACE IMAGE
      // ------------------------------------------

      let newImageUrl =
        oldData.image;


      if (body.image) {

        const imageParts =
          body.image.split(",");


        if (imageParts.length < 2) {

          return res.status(400).json({

            error:
              "Invalid image data"

          });
        }


        const file =
          Buffer.from(
            imageParts[1],
            "base64"
          );


        const safeTitle =
          body.title
            .replace(
              /[^a-z0-9]/gi,
              "-"
            )
            .toLowerCase();


        const imageBlob =
          await put(
            `wallpapers/${Date.now()}-${safeTitle}.jpg`,
            file,
            {
              access: "public",
              addRandomSuffix: true
            }
          );


        newImageUrl =
          imageBlob.url;


        // Delete old image
        if (oldData.image) {

          try {

            await del(
              oldData.image
            );

          } catch (deleteError) {

            console.error(
              "Old image delete error:",
              deleteError
            );

          }
        }
      }


      // ------------------------------------------
      // UPDATED DATA
      // ------------------------------------------

      const updatedData = {

        ...oldData,

        title:
          body.title,

        image:
          newImageUrl,

        updatedAt:
          Date.now()

      };


      // Get exact existing metadata pathname
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


      // Overwrite existing metadata
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
          "Wallpaper update failed"

      });
    }
  }



  // ==========================================
  // DELETE → DELETE WALLPAPER
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


      // Read metadata first
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


      // Delete metadata
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


      // Delete actual image
      if (data.image) {

        try {

          await del(
            data.image
          );

        } catch (imageError) {

          console.error(
            "Image delete error:",
            imageError
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
