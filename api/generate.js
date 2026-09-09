import { InferenceClient } from "@huggingface/inference";

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
Create a high-quality vertical mobile anime wallpaper.

${prompt}

Style:
beautiful anime artwork,
cinematic lighting,
detailed characters,
highly detailed background,
sharp focus,
professional composition,
vibrant colors,
dramatic atmosphere,
9:16 portrait composition,
mobile wallpaper,
high quality.
        `.trim();

        const hf = new InferenceClient(
            process.env.HF_TOKEN
        );

        const image = await hf.textToImage({
            model: "black-forest-labs/FLUX.1-schnell",
            provider: "auto",
            inputs: finalPrompt
        });

        // Convert generated Blob to Base64
        const arrayBuffer = await image.arrayBuffer();

        const base64 = Buffer
            .from(arrayBuffer)
            .toString("base64");

        return res.status(200).json({
            image: `data:image/png;base64,${base64}`
        });

    } catch (error) {

        console.error("HF ERROR:", error);

        return res.status(500).json({
            error: error?.message || "AI generation failed"
        });
    }
}
