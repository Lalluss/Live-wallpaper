import { put, list } from "@vercel/blob";

export default async function handler(req, res) {

  // GET → online wallpapers list
  if (req.method === "GET") {

    try {

      const result = await list({
        prefix: "wallpapers-data/"
      });

      const wallpapers = [];

      for (const blob of result.blobs) {

        try {

          const response = await fetch(blob.url);
          const data = await response.json();

          wallpapers.push(data);

        } catch (error) {
          console.error("Metadata read error:", error);
        }

      }

      wallpapers.sort(
        (a, b) => b.createdAt - a.createdAt
      );

      return res.status(200).json({
        wallpapers
      });

    } catch (error) {

      console.error(error);

      return res.status(500).json({
        error: "Failed to load wallpapers"
      });

    }
  }


  // POST → upload wallpaper
  if (req.method === "POST") {

    try {

      const body = req.body;

      if (
        !body ||
        !body.title ||
        !body.image
      ) {

        return res.status(400).json({
          error: "Title and image are required"
        });

      }


      // Convert base64 image to file
      const file = Buffer.from(
        body.image.split(",")[1],
        "base64"
      );


      const safeTitle =
        body.title
          .replace(/[^a-z0-9]/gi, "-")
          .toLowerCase();


      const timestamp = Date.now();


      // Upload actual wallpaper
      const imageBlob = await put(
        `wallpapers/${timestamp}-${safeTitle}.jpg`,
        file,
        {
          access: "public",
          addRandomSuffix: true
        }
      );


      // Save wallpaper information
      const wallpaperData = {

        title: body.title,

        image: imageBlob.url,

        createdAt: timestamp

      };


      await put(
        `wallpapers-data/${timestamp}-${safeTitle}.json`,
        JSON.stringify(wallpaperData),
        {
          access: "public",
          addRandomSuffix: true,
          contentType: "application/json"
        }
      );


      return res.status(200).json({
        success: true,
        wallpaper: wallpaperData
      });


    } catch (error) {

      console.error(error);

      return res.status(500).json({
        error: "Wallpaper upload failed"
      });

    }

  }


  return res.status(405).json({
    error: "Method not allowed"
  });

}
