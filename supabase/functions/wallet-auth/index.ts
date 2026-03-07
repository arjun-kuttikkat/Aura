// supabase/functions/wallet-auth/index.ts
// Wallet-based authentication: Ed25519 signature verification → Supabase JWT
//
// Flow:
// 1. Client requests nonce: POST { action: "nonce", walletAddress }
// 2. Server returns { nonce } (random challenge, stored in DB with TTL)
// 3. Client signs nonce with MWA signMessages()
// 4. Client sends: POST { action: "verify", walletAddress, nonce, signature }
// 5. Server verifies Ed25519 signature and returns Supabase JWT

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import * as jose from "https://deno.land/x/jose@v5.2.0/index.ts"
import { decode as decodeBase58 } from "npm:bs58"
import nacl from "npm:tweetnacl"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
const JWT_SECRET = Deno.env.get("SUPABASE_JWT_SECRET")!
const NONCE_TTL_MS = 5 * 60 * 1000 // 5 minutes

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, apikey, x-client-info",
}

serve(async (req: Request) => {
    if (req.method === "OPTIONS") {
        return new Response(null, { headers: corsHeaders })
    }

    if (req.method !== "POST") {
        return new Response("Method not allowed", { status: 405, headers: corsHeaders })
    }

    try {
        const { action, walletAddress, nonce, signature } = await req.json()

        if (!walletAddress || typeof walletAddress !== "string" || walletAddress.length < 32 || walletAddress.length > 44) {
            return new Response(JSON.stringify({ error: "Invalid walletAddress" }), {
                status: 400,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            })
        }

        const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

        // ═══════════════════════════════════════════════════════════════
        // Action: Request a nonce (challenge)
        // ═══════════════════════════════════════════════════════════════
        if (action === "nonce") {
            // Generate a random nonce
            const nonceBytes = crypto.getRandomValues(new Uint8Array(32))
            const nonceHex = Array.from(nonceBytes).map(b => b.toString(16).padStart(2, "0")).join("")

            // Store nonce in DB with expiry (upsert by wallet)
            const expiresAt = new Date(Date.now() + NONCE_TTL_MS).toISOString()
            await supabase
                .from("auth_nonces")
                .upsert({
                    wallet_address: walletAddress,
                    nonce: nonceHex,
                    expires_at: expiresAt,
                }, { onConflict: "wallet_address" })

            return new Response(JSON.stringify({ nonce: nonceHex }), {
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            })
        }

        // ═══════════════════════════════════════════════════════════════
        // Action: Verify signature and return JWT
        // ═══════════════════════════════════════════════════════════════
        if (action === "verify") {
            if (!nonce || !signature) {
                return new Response(JSON.stringify({ error: "nonce and signature required" }), {
                    status: 400,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }

            // Fetch stored nonce
            const { data: nonceRow, error: nonceError } = await supabase
                .from("auth_nonces")
                .select("nonce, expires_at")
                .eq("wallet_address", walletAddress)
                .single()

            if (nonceError || !nonceRow) {
                return new Response(JSON.stringify({ error: "No nonce found. Request a nonce first." }), {
                    status: 401,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }

            // Check expiry
            if (new Date(nonceRow.expires_at) < new Date()) {
                // Clean up expired nonce
                await supabase.from("auth_nonces").delete().eq("wallet_address", walletAddress)
                return new Response(JSON.stringify({ error: "Nonce expired. Request a new one." }), {
                    status: 401,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }

            // Verify nonce matches
            if (nonceRow.nonce !== nonce) {
                return new Response(JSON.stringify({ error: "Nonce mismatch." }), {
                    status: 401,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }

            // Verify Ed25519 signature
            // The message that was signed is the nonce as a UTF-8 string
            const messageBytes = new TextEncoder().encode(nonce)
            const signatureBytes = decodeBase58(signature)
            const publicKeyBytes = decodeBase58(walletAddress)

            const isValid = nacl.sign.detached.verify(messageBytes, signatureBytes, publicKeyBytes)

            if (!isValid) {
                return new Response(JSON.stringify({ error: "Invalid signature." }), {
                    status: 403,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }

            // Consume the nonce (one-time use)
            await supabase.from("auth_nonces").delete().eq("wallet_address", walletAddress)

            // Ensure profile exists (upsert)
            await supabase
                .from("profiles")
                .upsert({ wallet_address: walletAddress }, { onConflict: "wallet_address", ignoreDuplicates: true })

            // Fetch user's profile ID for the JWT sub claim
            const { data: profile } = await supabase
                .from("profiles")
                .select("id")
                .eq("wallet_address", walletAddress)
                .single()

            // Build a Supabase-compatible JWT with wallet_address in app_metadata
            const secret = new TextEncoder().encode(JWT_SECRET)
            const now = Math.floor(Date.now() / 1000)
            const jwt = await new jose.SignJWT({
                aud: "authenticated",
                role: "authenticated",
                sub: profile?.id || walletAddress,
                wallet_address: walletAddress,
                app_metadata: { wallet_address: walletAddress },
                user_metadata: { wallet_address: walletAddress },
            })
                .setProtectedHeader({ alg: "HS256", typ: "JWT" })
                .setIssuedAt(now)
                .setExpirationTime(now + 60 * 60 * 24) // 24 hours
                .sign(secret)

            return new Response(JSON.stringify({
                access_token: jwt,
                token_type: "bearer",
                expires_in: 86400,
                wallet_address: walletAddress,
            }), {
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            })
        }

        return new Response(JSON.stringify({ error: "Invalid action. Use 'nonce' or 'verify'." }), {
            status: 400,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
        })
    } catch (e: any) {
        return new Response(JSON.stringify({ error: e.message }), {
            status: 500,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
        })
    }
})
