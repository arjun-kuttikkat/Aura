import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Only allow safe, read-only RPC methods through the proxy
const ALLOWED_METHODS = new Set([
    'getLatestBlockhash',
    'getBalance',
    'getAccountInfo',
    'getMinimumBalanceForRentExemption',
    'getSignatureStatuses',
    'getTransaction',
    'getTokenAccountBalance',
    'getTokenAccountsByOwner',
    'getSlot',
    'getBlockHeight',
    'getHealth',
]);

// Simple in-memory rate limiter: max 30 requests per minute per IP
const rateLimitMap = new Map<string, { count: number; resetAt: number }>();
const RATE_LIMIT = 30;
const RATE_WINDOW_MS = 60_000;

function checkRateLimit(ip: string): boolean {
    const now = Date.now();
    const entry = rateLimitMap.get(ip);
    if (!entry || now > entry.resetAt) {
        rateLimitMap.set(ip, { count: 1, resetAt: now + RATE_WINDOW_MS });
        return true;
    }
    if (entry.count >= RATE_LIMIT) return false;
    entry.count++;
    return true;
}

serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }

    try {
        const clientIp = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() || 'unknown';
        if (!checkRateLimit(clientIp)) {
            return new Response(JSON.stringify({ error: 'Rate limit exceeded. Try again in 1 minute.' }), {
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                status: 429,
            });
        }

        const rpcPayload = await req.json();
        const HELIUS_KEY = Deno.env.get('HELIUS_API_KEY');

        if (!HELIUS_KEY) {
            throw new Error("Missing HELIUS_API_KEY in environment");
        }

        // Validate the requested method is in the whitelist
        const method = rpcPayload?.method;
        if (!method || !ALLOWED_METHODS.has(method)) {
            return new Response(JSON.stringify({ error: `Method "${method}" is not allowed through this proxy.` }), {
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                status: 403,
            });
        }

        const HELIUS_URL = `https://mainnet.helius-rpc.com/?api-key=${HELIUS_KEY}`;

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
