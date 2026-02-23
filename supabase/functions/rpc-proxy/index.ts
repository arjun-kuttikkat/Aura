import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

serve(async (req) => {
    // Handle CORS
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }

    try {
        const rpcPayload = await req.json();
        const HELIUS_KEY = Deno.env.get('HELIUS_API_KEY');

        if (!HELIUS_KEY) {
            throw new Error("Missing HELIUS_API_KEY in environment");
        }

        const HELIUS_URL = `https://mainnet.helius-rpc.com/?api-key=${HELIUS_KEY}`;

        // Pass the JSON-RPC request unchanged to Helius
        const response = await fetch(HELIUS_URL, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(rpcPayload),
        });

        const data = await response.json();

        return new Response(JSON.stringify(data), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            status: response.status
        });

    } catch (err: any) {
        return new Response(JSON.stringify({ error: err.message }), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            status: 400
        });
    }
});
