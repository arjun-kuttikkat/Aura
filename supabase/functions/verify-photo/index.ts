import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * verify-photo — AI Vision verification via Groq.
 *
 * Two modes:
 * - aura_check: Single image; score authenticity of a live capture.
 * - item_match: listingId + photoBase64; fetch listing's reference image and compare.
 *   "Does the buyer's photo show the SAME physical item as the listing photo?"
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

        const isAuraCheck = checkType === "aura_check";
        const isItemMatch = !isAuraCheck && listingId;

        let systemPrompt: string;
        const content: any[] = [{ type: "text", text: "" }, { type: "image_url", image_url: { url: "" } }];

        // Determine MIME type for user photo
        let mimeType = "image/jpeg";
        if (photoBase64.startsWith("/9j/")) mimeType = "image/jpeg";
        else if (photoBase64.startsWith("iVBOR")) mimeType = "image/png";

        const userImageUrl = `data:${mimeType};base64,${photoBase64.slice(0, 180000)}`;
        content[1].image_url.url = userImageUrl;

        if (isItemMatch) {
            // Fetch listing and its first image from Supabase
            const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
            const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
            const supabase = createClient(supabaseUrl, supabaseKey);

            const { data: listing, error: listError } = await supabase
                .from('marketplace_listings')
                .select('id, images')
                .eq('id', listingId)
                .single();

            if (listError || !listing) {
                return new Response(
                    JSON.stringify({ rating: 0, pass: false, feedback: "Listing not found. Cannot verify item." }),
                    { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 },
                );
            }

            const images = listing?.images;
            const refImageUrl = images == null ? null : Array.isArray(images) ? (images[0] ?? null) : (typeof images === 'string' ? images : null);

            if (!refImageUrl || typeof refImageUrl !== 'string') {
                return new Response(
                    JSON.stringify({ rating: 0, pass: false, feedback: "Listing has no reference image. Cannot verify item." }),
                    { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 },
                );
            }

            // Fetch reference image and convert to base64 for Groq (they accept URLs, so we can pass URL directly if it's public)
            let refImageForGroq: string;
            try {
                const imgRes = await fetch(refImageUrl);
                if (!imgRes.ok) throw new Error("Failed to fetch listing image");
                const buf = await imgRes.arrayBuffer();
                const b64 = btoa(String.fromCharCode(...new Uint8Array(buf)));
                const refMime = (imgRes.headers.get("content-type") || "image/jpeg").split(";")[0].trim();
                refImageForGroq = `data:${refMime};base64,${b64.slice(0, 180000)}`;
            } catch (_) {
                // If we can't fetch, pass the URL and hope Groq can fetch it (some APIs support URLs)
                refImageForGroq = refImageUrl;
            }

            systemPrompt = `You are an item-matching inspector for the Aura marketplace.
Image 1: The buyer's photo of the physical item in front of them (may have different lighting, be in a bag/plastic wrap, or different angle).
Image 2: The listing's reference photo (what the seller listed).

Question: Does Image 1 show the SAME physical item as Image 2? Consider only whether it is the same product (model, key features, condition).
DO NOT reject for: different lighting, item inside plastic bag or packaging, slight angle difference, indoor vs outdoor, shadows. Pass if the same item is clearly visible.
Respond ONLY with valid JSON: {"rating": <0-100 where 70+ means same item>, "pass": <true if same item>, "feedback": "<1-2 sentence>"}`;

            content[0].text = systemPrompt;
            content.push({ type: "image_url", image_url: { url: refImageForGroq } });
        } else {
            // aura_check or generic single-image
            systemPrompt = isAuraCheck
                ? "You are an AI authenticity inspector for the Aura marketplace. Analyze this photo of the user's physical environment. Score the authenticity of this being a real, live capture (not a screenshot, stock photo, or AI-generated image). Respond ONLY with valid JSON: {\"rating\": <0-100>, \"pass\": <true if rating >= 70>, \"feedback\": \"<1-2 sentence analysis>\"}"
                : "You are an AI product photo inspector for the Aura marketplace. Analyze this listing photo. Score how authentic and genuine this product photo appears (not a screenshot, not AI-generated, not a stock photo, shows a real physical item). Respond ONLY with valid JSON: {\"rating\": <0-100>, \"pass\": <true if rating >= 70>, \"feedback\": \"<1-2 sentence assessment of the photo>\"}";
            content[0].text = systemPrompt;
        }

        const groqResponse = await fetch("https://api.groq.com/openai/v1/chat/completions", {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${groqApiKey}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                model: "meta-llama/llama-4-scout-17b-16e-instruct",
                messages: [{ role: "user", content }],
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
        const textContent = groqData.choices?.[0]?.message?.content || "{}";

        let result: { rating?: number; pass?: boolean; feedback?: string };
        try {
            result = JSON.parse(textContent);
        } catch {
            result = { rating: 50, pass: false, feedback: "Could not parse verification result." };
        }

        const rating = Math.min(100, Math.max(0, Number(result.rating) ?? 50));
        const pass = Boolean(result.pass) || rating >= 70;
        const feedback = result.feedback || (pass ? "Item match confirmed." : "Item match could not be confirmed.");

        return new Response(
            JSON.stringify({ rating, pass, feedback }),
            { headers: { ...corsHeaders, 'Content-Type': 'application/json' } },
        );
    } catch (error: any) {
        return new Response(
            JSON.stringify({ rating: 0, pass: false, feedback: error?.message || "Verification failed." }),
            { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 },
        );
    }
});
