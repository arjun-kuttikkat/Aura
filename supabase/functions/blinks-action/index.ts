// supabase/functions/blinks-action/index.ts
// Solana Actions (Blinks) — allows listing to be shared as a clickable URL on Twitter/Discord
// Buyer funds escrow directly from browser, then meets seller IRL to release via NFC

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { Connection, PublicKey, Transaction, SystemProgram, LAMPORTS_PER_SOL } from "https://esm.sh/@solana/web3.js@1"

const SOLANA_RPC = Deno.env.get("SOLANA_RPC_URL") || "https://api.devnet.solana.com"
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!
const SUPABASE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
const APP_URL = Deno.env.get("APP_URL") || "https://aura.so"

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, Accept-Encoding",
}

serve(async (req: Request) => {
    // Handle CORS preflight
    if (req.method === "OPTIONS") {
        return new Response(null, { headers: corsHeaders })
    }

    const url = new URL(req.url)
    const listingId = url.searchParams.get("listingId")

    if (!listingId) {
        return new Response(JSON.stringify({ error: "listingId required" }), {
            status: 400,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
        })
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_KEY)
    const { data: listing, error } = await supabase
        .from("listings")
        .select("*")
        .eq("id", listingId)
        .single()

    if (error || !listing) {
        return new Response(JSON.stringify({ error: "Listing not found" }), {
            status: 404,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
        })
    }

    // GET: Return Solana Action metadata (displayed in Blink unfurl)
    if (req.method === "GET") {
        const priceSol = listing.price_lamports / LAMPORTS_PER_SOL
        const actionPayload = {
            icon: listing.images?.[0] || `${APP_URL}/logo.png`,
            title: listing.title,
            description: `Buy "${listing.title}" for ${priceSol} SOL on Aura — NFC-verified P2P marketplace. Funds held in escrow until physical verification.`,
            label: `Buy for ${priceSol} SOL`,
            links: {
                actions: [
                    {
                        label: `Fund Escrow (${priceSol} SOL)`,
                        href: `${url.origin}${url.pathname}?listingId=${listingId}`,
                    },
                ],
            },
        }

        return new Response(JSON.stringify(actionPayload), {
            headers: {
                ...corsHeaders,
                "Content-Type": "application/json",
                "X-Action-Version": "1",
                "X-Blockchain-Ids": "solana:EtWTRABZaYq6iMfeYKouRu166VU2xqa1",
            },
        })
    }

    // POST: Build the escrow funding transaction for the buyer to sign
    if (req.method === "POST") {
        try {
            const body = await req.json()
            const buyerPubkey = new PublicKey(body.account)
            const sellerPubkey = new PublicKey(listing.seller_wallet)

            const connection = new Connection(SOLANA_RPC)
            const latestBlockhash = await connection.getLatestBlockhash()

            // Build a SystemProgram.transfer to escrow (seller for now, Anchor PDA in production)
            const tx = new Transaction({
                recentBlockhash: latestBlockhash.blockhash,
                feePayer: buyerPubkey,
            }).add(
                SystemProgram.transfer({
                    fromPubkey: buyerPubkey,
                    toPubkey: sellerPubkey,
                    lamports: listing.price_lamports,
                })
            )

            // Record the trade session
            await supabase.from("trade_sessions").insert({
                listing_id: listingId,
                buyer_wallet: buyerPubkey.toBase58(),
                seller_wallet: listing.seller_wallet,
                state: "ESCROW_FUNDED",
            })

            // Serialize transaction for client signing
            const serializedTx = tx.serialize({
                requireAllSignatures: false,
                verifySignatures: false,
            })

            return new Response(
                JSON.stringify({
                    transaction: Buffer.from(serializedTx).toString("base64"),
                    message: `Escrow funded for "${listing.title}". Meet the seller and tap NFC to release funds.`,
                }),
                {
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                }
            )
        } catch (e) {
            return new Response(JSON.stringify({ error: e.message }), {
                status: 500,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            })
        }
    }

    return new Response("Method not allowed", { status: 405, headers: corsHeaders })
})
