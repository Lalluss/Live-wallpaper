import crypto from "crypto";

function createToken() {
  const timestamp = Date.now().toString();

  const signature = crypto
    .createHmac("sha256", process.env.ADMIN_PASSWORD)
    .update(timestamp)
    .digest("hex");

  return `${timestamp}.${signature}`;
}

export default async function handler(req, res) {

  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  try {

    const { password } = req.body || {};

    if (!password) {
      return res.status(400).json({
        error: "Password required"
      });
    }

    if (password !== process.env.ADMIN_PASSWORD) {
      return res.status(401).json({
        error: "Wrong password"
      });
    }

    const token = createToken();

    res.setHeader(
      "Set-Cookie",
      `admin_session=${token}; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=86400`
    );

    return res.status(200).json({
      success: true
    });

  } catch (error) {

    console.error(error);

    return res.status(500).json({
      error: "Login failed"
    });

  }
}
