import { handleUpload } from "@vercel/blob/client";
import crypto from "crypto";

function verifyAdminSession(req) {
  const cookies = req.headers.cookie || "";

  const match = cookies.match(
    /(?:^|;\s*)admin_session=([^;]+)/
  );

  if (!match) return false;

  const token = match[1];
  const parts = token.split(".");

  if (parts.length !== 2) return false;

  const timestamp = parts[0];
  const signature = parts[1];

  const timestampNumber = Number(timestamp);

  if (!Number.isFinite(timestampNumber)) {
    return false;
  }

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
  } catch {
    return false;
  }
}

export default async function handler(req, res) {

  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  if (!verifyAdminSession(req)) {
    return res.status(401).json({
      error: "Admin login required"
    });
  }

  try {

    // IMPORTANT:
    // Vercel Blob expects the client-upload
    // request body as JSON.
    const body = req.body;

    if (!body) {
      return res.status(400).json({
        error: "Missing request body"
      });
    }

    const jsonResponse = await handleUpload({
      body,
      request: req,

      onBeforeGenerateToken: async (
        pathname
      ) => {

        const isVideo =
          pathname.startsWith(
            "wallpapers-live/"
          ) ||
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

          maximumSizeInBytes:
            100 * 1024 * 1024,

          addRandomSuffix: true
        };
      },

      onUploadCompleted: async ({
        blob
      }) => {
        console.log(
          "Blob upload completed:",
          blob.url
        );
      }
    });

    return res.status(200).json(
      jsonResponse
    );

  } catch (error) {

    console.error(
      "BLOB TOKEN ERROR:",
      error
    );

    return res.status(400).json({
      error:
        error?.message ||
        "Failed to create Blob client token"
    });
  }
}
