import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/** Serves receipt NFT metadata JSON. No domain needed - uses Supabase project URL. */
serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }
    const metadata = {
        name: "Aura Trade Receipt",
        description: "On-chain proof of verified trade completion on Aura marketplace.",
        image: "https://placehold.co/400x400/1a1a2e/eee?text=Aura+Receipt",
        external_url: "https://github.com",
        attributes: [
            { trait_type: "Type", value: "Trade Receipt" },
            { trait_type: "Platform", value: "Aura" },
        ],
    };
    return new Response(JSON.stringify(metadata), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
});
