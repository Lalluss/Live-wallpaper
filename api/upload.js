import { handleUpload } from "@vercel/blob/client";

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({
      ok: false,
      error: "Method not allowed"
    });
  }

  try {
    console.log("UPLOAD API CALLED");

    console.log(
      "BLOB TOKEN EXISTS:",
      !!process.env.BLOB_READ_WRITE_TOKEN
    );

    const body = req.body;

    console.log(
      "REQUEST BODY:",
      JSON.stringify(body)
    );

    if (!body) {
      return res.status(400).json({
        ok: false,
        error: "Request body is empty"
      });
    }

    const result = await handleUpload({
      body,
      request: req,

      onBeforeGenerateToken: async (pathname) => {
        console.log(
          "GENERATING TOKEN FOR:",
          pathname
        );

        const isVideo =
          /\.(mp4|webm|mov|m4v)$/i.test(
            pathname
          );

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
        console.log(
          "UPLOAD COMPLETED:",
          blob.url
        );
      }
    });

    console.log(
      "TOKEN CREATED SUCCESSFULLY"
    );

    return res.status(200).json(result);

  } catch (error) {

    console.error(
      "REAL BLOB ERROR:",
      error
    );

    return res.status(500).json({
      ok: false,

      error:
        error?.message ||
        String(error),

      name:
        error?.name ||
        "UnknownError",

      stack:
        error?.stack ||
        null
    });
  }
}
