// supabase/functions/wallet-auth/index.ts
// Wallet-based authentication: Ed25519 signature verification → Supabase JWT
//
// Flow:
// 1. Client requests nonce: POST { action: "nonce", walletAddress }
// 2. Server returns { nonce } (random challenge, stored in DB with TTL)
// 3. Client signs nonce with MWA signMessages()
// 4. Client sends: POST { action: "verify", walletAddress, nonce, signature }
// 5. Server verifies Ed25519 signature and returns Supabase JWT

import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

// ── Base58 decoder (inline, no external dep) ──
const BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
function decodeBase58(s: string): Uint8Array {
    const bytes: number[] = [0]
    for (const c of s) {
        const carry = BASE58_ALPHABET.indexOf(c)
        if (carry < 0) throw new Error("Invalid base58 character")
        for (let j = 0; j < bytes.length; j++) {
            const x = bytes[j] * 58 + carry
            bytes[j] = x & 0xff
            if (j + 1 === bytes.length) { if (x >> 8) bytes.push(x >> 8) }
            else bytes[j + 1] += x >> 8
        }
    }
    // leading zeros
    for (const c of s) { if (c !== "1") break; bytes.push(0) }
    return new Uint8Array(bytes.reverse())
}

// ── Ed25519 verify via Web Crypto ──
async function ed25519Verify(message: Uint8Array, signature: Uint8Array, publicKey: Uint8Array): Promise<boolean> {
    const key = await crypto.subtle.importKey(
        "raw", publicKey, { name: "Ed25519" }, false, ["verify"]
    )
    return crypto.subtle.verify("Ed25519", key, signature, message)
}

// ── Minimal HS256 JWT (no jose dep) ──
function base64url(buf: Uint8Array): string {
    return btoa(String.fromCharCode(...buf)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_")
}
async function signJwt(payload: Record<string, unknown>, secret: string): Promise<string> {
    const header = base64url(new TextEncoder().encode(JSON.stringify({ alg: "HS256", typ: "JWT" })))
    const body = base64url(new TextEncoder().encode(JSON.stringify(payload)))
    const data = new TextEncoder().encode(`${header}.${body}`)
    const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"])
    const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, data))
    return `${header}.${body}.${base64url(sig)}`
}

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
const JWT_SECRET = Deno.env.get("SUPABASE_JWT_SECRET")!
const NONCE_TTL_MS = 5 * 60 * 1000 // 5 minutes

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, apikey, x-client-info",
}

Deno.serve(async (req: Request) => {
    if (req.method === "OPTIONS") {
        return new Response(null, { headers: corsHeaders })
    }

    if (req.method !== "POST") {
        return new Response("Method not allowed", { status: 405, headers: corsHeaders })
    }

    try {
        const { action, walletAddress, signatureBase64 } = await req.json()

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
            // Ensure profile exists first (auth_nonces has FK to profiles) — needed for new users
            await supabase
                .from("profiles")
                .upsert({ wallet_address: walletAddress }, { onConflict: "wallet_address", ignoreDuplicates: true })

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
            if (!signatureBase64) {
                return new Response(JSON.stringify({ error: "signatureBase64 required" }), {
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

            const nonce = nonceRow.nonce

            // Verify Ed25519 signature — client signs "Aura wallet-auth nonce: " + nonce
            const message = `Aura wallet-auth nonce: ${nonce}`
            const messageBytes = new TextEncoder().encode(message)
            let signatureBytes: Uint8Array
            try {
                signatureBytes = Uint8Array.from(atob(signatureBase64), c => c.charCodeAt(0))
            } catch {
                return new Response(JSON.stringify({ error: "Invalid signature encoding (expected base64)" }), {
                    status: 400,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }
            const publicKeyBytes = decodeBase58(walletAddress)

            const isValid = await ed25519Verify(messageBytes, signatureBytes, publicKeyBytes)

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
            const now = Math.floor(Date.now() / 1000)
            const jwt = await signJwt({
                aud: "authenticated",
                role: "authenticated",
                sub: profile?.id || walletAddress,
                wallet_address: walletAddress,
                app_metadata: { wallet_address: walletAddress },
                user_metadata: { wallet_address: walletAddress },
                iat: now,
                exp: now + 60 * 60 * 24, // 24 hours
            }, JWT_SECRET)

            return new Response(JSON.stringify({
                token: jwt,
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
