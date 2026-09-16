import { handleUpload } from "@vercel/blob/client";

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  try {
    const body = await handleUpload({
      body: req.body,
      request: req,
      onBeforeGenerateToken: async (pathname) => {

        const isVideo =
          pathname.startsWith("wallpapers-live/") ||
          /\.(mp4|webm|mov|m4v)$/i.test(pathname);

        return {
          allowedContentTypes: isVideo
            ? [
                "video/mp4",
                "video/webm",
                "video/quicktime",
                "video/x-m4v"
              ]
            : [
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/gif"
              ],

          addRandomSuffix: true
        };
      },

      onUploadCompleted: async ({ blob }) => {
        console.log("Upload completed:", blob.url);
      }
    });

    return res.status(200).json(body);

  } catch (error) {
    console.error("Upload error:", error);

    return res.status(500).json({
      error: error.message || "Upload failed"
    });
  }
}
