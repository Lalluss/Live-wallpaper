import { handleUpload } from "@vercel/blob/client";
import crypto from "crypto";

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

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  try {
    // Admin authentication
    if (!verifyAdmin(req)) {
      return res.status(401).json({
        error: "Unauthorized"
      });
    }

    const jsonResponse = await handleUpload({
      request: req,

      body: req.body,

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
        console.log(
          "Vercel Blob upload completed:",
          blob.url
        );
      }
    });

    return res.status(200).json(jsonResponse);

  } catch (error) {
    console.error(
      "Vercel Blob client upload error:",
      error
    );

    return res.status(500).json({
      error:
        error?.message ||
        "Failed to retrieve the client token"
    });
  }
          }
