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

Beautiful anime artwork,
cinematic lighting,
highly detailed character,
detailed environment,
sharp focus,
professional composition,
dramatic atmosphere,
vibrant colors,
9:16 portrait composition,
mobile wallpaper quality.
        `.trim();

        const hf = new InferenceClient(process.env.HF_TOKEN);

        const image = await hf.textToImage({
            model: "black-forest-labs/FLUX.1-schnell",
            provider: "fal-ai",
            inputs: finalPrompt
        });

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
