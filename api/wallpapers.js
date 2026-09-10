import { put } from "@vercel/blob";

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  try {
    const body = req.body;

    if (!body || !body.title || !body.image) {
      return res.status(400).json({
        error: "Title and image are required"
      });
    }

    const file = Buffer.from(
      body.image.split(",")[1],
      "base64"
    );

    const filename =
      `wallpapers/${Date.now()}-${body.title
        .replace(/[^a-z0-9]/gi, "-")
        .toLowerCase()}.jpg`;

    const blob = await put(
      filename,
      file,
      {
        access: "public",
        addRandomSuffix: true
      }
    );

    return res.status(200).json({
      success: true,
      title: body.title,
      image: blob.url
    });

  } catch (error) {

    console.error(error);

    return res.status(500).json({
      error: "Wallpaper upload failed"
    });
  }
}
