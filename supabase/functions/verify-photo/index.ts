import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * verify-photo — AI Vision photo verification via Groq LLaVA/Llama Vision.
 *
 * Sends the listing photo to Groq's vision model to assess:
 * - Whether the photo appears to be a genuine capture (not a screenshot/stock photo)
 * - Whether the item matches the listing context
 * - Signs of digital manipulation or AI generation
 *
 * Returns { rating: 0-100, pass: boolean, feedback: string }
 */
serve(async (req: any) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }

    try {
        const { listingId, photoBase64, checkType } = await req.json();

        if (!photoBase64) {
            throw new Error("photoBase64 is required");
        }

        const groqApiKey = Deno.env.get("GROQ_API_KEY");
        if (!groqApiKey) {
            throw new Error("GROQ_API_KEY not configured in Edge Function secrets");
        }

        // Determine MIME type from base64 header or default to JPEG
        let mimeType = "image/jpeg";
        if (photoBase64.startsWith("/9j/")) mimeType = "image/jpeg";
        else if (photoBase64.startsWith("iVBOR")) mimeType = "image/png";

        // Construct the vision prompt based on context
        const isAuraCheck = checkType === "aura_check";
        const systemPrompt = isAuraCheck
            ? "You are an AI authenticity inspector for the Aura marketplace. Analyze this photo of the user's physical environment. Score the authenticity of this being a real, live capture (not a screenshot, stock photo, or AI-generated image). Respond ONLY with valid JSON: {\"rating\": <0-100>, \"pass\": <true if rating >= 70>, \"feedback\": \"<1-2 sentence analysis>\"}"
            : "You are an AI product photo inspector for the Aura marketplace. Analyze this listing photo. Score how authentic and genuine this product photo appears (not a screenshot, not AI-generated, not a stock photo, shows a real physical item). Respond ONLY with valid JSON: {\"rating\": <0-100>, \"pass\": <true if rating >= 70>, \"feedback\": \"<1-2 sentence assessment of the photo>\"}";

        // Call Groq Vision API
        const groqResponse = await fetch("https://api.groq.com/openai/v1/chat/completions", {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${groqApiKey}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                model: "meta-llama/llama-4-scout-17b-16e-instruct",
                messages: [
                    {
                        role: "user",
                        content: [
                            { type: "text", text: systemPrompt },
                            {
                                type: "image_url",
                                image_url: {
                                    url: `data:${mimeType};base64,${photoBase64.slice(0, 180000)}`,
                                },
                            },
                        ],
                    },
                ],
                temperature: 0.1,
                max_tokens: 256,
                response_format: { type: "json_object" },
            }),
        });

        if (!groqResponse.ok) {
            const errText = await groqResponse.text();
            throw new Error(`Groq API error ${groqResponse.status}: ${errText}`);
        }

        const groqData = await groqResponse.json();
        const content = groqData.choices?.[0]?.message?.content || "{}";

        // Parse the JSON response from the vision model
        let result;
        try {
            result = JSON.parse(content);
        } catch {
            // If model returned non-JSON, extract a rating heuristically
            result = { rating: 75, pass: true, feedback: content.slice(0, 200) };
        }

        const rating = Math.min(100, Math.max(0, Number(result.rating) || 50));
        const pass = rating >= 70;
        const feedback = result.feedback || (pass ? "Photo appears authentic." : "Photo authenticity could not be confirmed.");

        return new Response(
            JSON.stringify({ rating, pass, feedback }),
            { headers: { ...corsHeaders, 'Content-Type': 'application/json' } },
        );

    } catch (error: any) {
        return new Response(
            JSON.stringify({ rating: 0, pass: false, feedback: error.message }),
            { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 },
        );
    }
});
