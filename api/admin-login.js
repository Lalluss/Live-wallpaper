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
