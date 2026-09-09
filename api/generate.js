export default async function handler(req, res) {
    if (req.method !== "POST") {
        return res.status(405).json({
            error: "Method not allowed"
        });
    }

    try {
        const { prompt } = req.body || {};

        if (!prompt || !prompt.trim()) {
            return res.status(400).json({
                error: "Prompt is required"
            });
        }

        const finalPrompt = `
Create a high-quality vertical mobile wallpaper.

${prompt}

Style: beautiful anime artwork, cinematic lighting,
high detail, sharp focus, detailed background,
professional composition, vibrant colors,
9:16 portrait composition, 4K wallpaper quality.
        `.trim();

        const response = await fetch(
            "https://router.huggingface.co/hf-inference/models/black-forest-labs/FLUX.1-schnell",
            {
                method: "POST",
                headers: {
                    "Authorization": `Bearer ${process.env.HF_TOKEN}`,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    inputs: finalPrompt
                })
            }
        );

        if (!response.ok) {
            const errorText = await response.text();

            return res.status(response.status).json({
                error: errorText || "AI generation failed"
            });
        }

        const imageBuffer = await response.arrayBuffer();

        const base64Image = Buffer
            .from(imageBuffer)
            .toString("base64");

        return res.status(200).json({
            image: `data:image/png;base64,${base64Image}`
        });

    } catch (error) {
        console.error(error);

        return res.status(500).json({
            error: "Something went wrong while generating the wallpaper."
        });
    }
}
