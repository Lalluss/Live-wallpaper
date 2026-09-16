import { put, list } from "@vercel/blob";
import crypto from "crypto";


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


  // Prevent timingSafeEqual length errors
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



export default async function handler(req, res) {


  // ==========================================
  // GET → Load online wallpapers
  // ==========================================

  if (req.method === "GET") {

    try {

      const result = await list({
        prefix: "wallpapers-data/"
      });


      const wallpapers = [];


      for (const blob of result.blobs) {

        try {

          const response = await fetch(
            blob.url
          );

          const data =
            await response.json();


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
          b.createdAt - a.createdAt
      );


      return res.status(200).json({
        wallpapers
      });


    } catch (error) {

      console.error(error);


      return res.status(500).json({
        error:
          "Failed to load wallpapers"
      });

    }

  }



  // ==========================================
  // POST → Upload wallpaper
  // ==========================================

  if (req.method === "POST") {


    // ------------------------------------------
    // Check admin login
    // ------------------------------------------

    if (!verifyAdminSession(req)) {

      return res.status(401).json({
        error:
          "Admin login required"
      });

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



      // ------------------------------------------
      // Convert base64 image to file
      // ------------------------------------------

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



      // ------------------------------------------
      // Create safe filename
      // ------------------------------------------

      const safeTitle =
        body.title
          .replace(/[^a-z0-9]/gi, "-")
          .toLowerCase();


      const timestamp =
        Date.now();



      // ------------------------------------------
      // Upload actual wallpaper
      // ------------------------------------------

      const imageBlob =
        await put(
          `wallpapers/${timestamp}-${safeTitle}.jpg`,
          file,
          {
            access: "public",
            addRandomSuffix: true
          }
        );



      // ------------------------------------------
      // Save wallpaper information
      // ------------------------------------------

      const wallpaperData = {

        title:
          body.title,

        image:
          imageBlob.url,

        createdAt:
          timestamp

      };



      // ------------------------------------------
      // Save metadata
      // ------------------------------------------

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



      // ------------------------------------------
      // Success
      // ------------------------------------------

      return res.status(200).json({

        success: true,

        wallpaper:
          wallpaperData

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
  // Other methods
  // ==========================================

  return res.status(405).json({

    error:
      "Method not allowed"

  });

      }
