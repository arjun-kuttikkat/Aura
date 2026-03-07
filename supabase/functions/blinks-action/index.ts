// supabase/functions/blinks-action/index.ts
// Solana Actions (Blinks) — allows listing to be shared as a clickable URL on Twitter/Discord
// Buyer funds escrow directly from browser, then meets seller IRL to release via NFC

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { Connection, PublicKey, Transaction, SystemProgram, LAMPORTS_PER_SOL } from "https://esm.sh/@solana/web3.js@1"

const SOLANA_RPC = Deno.env.get("SOLANA_RPC_URL")
if (!SOLANA_RPC) throw new Error("SOLANA_RPC_URL env var is required — set in Supabase Edge Function secrets")
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!
const SUPABASE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
const APP_URL = Deno.env.get("APP_URL") || "https://aura.so"
const TREASURY_WALLET = Deno.env.get("TREASURY_WALLET") || ""
const PLATFORM_FEE_BPS = Number(Deno.env.get("PLATFORM_FEE_BPS") || "200")
if (!TREASURY_WALLET) throw new Error("TREASURY_WALLET env var is required")

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
        .from("marketplace_listings")
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

    // POST: Build the Anchor escrow funding transaction for the buyer to sign
    if (req.method === "POST") {
        try {
            const body = await req.json()
            const buyerPubkey = new PublicKey(body.account)
            const sellerPubkey = new PublicKey(listing.seller_wallet)
            const treasuryPubkey = new PublicKey(TREASURY_WALLET)

            const connection = new Connection(SOLANA_RPC)
            const latestBlockhash = await connection.getLatestBlockhash()
            const PROGRAM_ID = new PublicKey("BMKWLYrXtuuxp4TA4yNhrs9LbomR1fMdbrko6R7Qj5WM")

            // Derive Anchor PDAs
            const [escrowPda] = PublicKey.findProgramAddressSync(
                [Buffer.from("escrow"), Buffer.from(listingId)],
                PROGRAM_ID
            )
            const [vaultPda] = PublicKey.findProgramAddressSync(
                [Buffer.from("vault"), escrowPda.toBuffer()],
                PROGRAM_ID
            )

            // Anchor discriminator for "global:initialize"
            const crypto = await import("node:crypto")
            const discriminator = crypto.createHash("sha256")
                .update("global:initialize")
                .digest()
                .slice(0, 8)

            // Borsh-serialize: [8 disc][8 amount LE][4+N listing_id][32 seller_wallet][2 fee_bps][32 treasury_wallet][1 fee_exempt]
            const listingIdBytes = Buffer.from(listingId)
            const dataLen = 8 + 8 + 4 + listingIdBytes.length + 32 + 2 + 32 + 1
            const data = Buffer.alloc(dataLen)
            let offset = 0
            discriminator.copy(data, offset); offset += 8
            data.writeBigUInt64LE(BigInt(listing.price_lamports), offset); offset += 8
            data.writeUInt32LE(listingIdBytes.length, offset); offset += 4
            listingIdBytes.copy(data, offset); offset += listingIdBytes.length
            sellerPubkey.toBuffer().copy(data, offset)
            offset += 32
            data.writeUInt16LE(PLATFORM_FEE_BPS, offset)
            offset += 2
            treasuryPubkey.toBuffer().copy(data, offset)
            offset += 32
            data.writeUInt8(0, offset) // fee_exempt=false for action-initiated purchases

            const tx = new Transaction({
                recentBlockhash: latestBlockhash.blockhash,
                feePayer: buyerPubkey,
            }).add({
                programId: PROGRAM_ID,
                keys: [
                    { pubkey: buyerPubkey, isSigner: true, isWritable: true },
                    { pubkey: escrowPda, isSigner: false, isWritable: true },
                    { pubkey: vaultPda, isSigner: false, isWritable: true },
                    { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
                ],
                data,
            })

            // Idempotency guard: check for existing non-failed session for this listing+buyer
            const { data: existingSession } = await supabase
                .from("trade_sessions")
                .select("id, state")
                .eq("listing_id", listingId)
                .eq("buyer_wallet", buyerPubkey.toBase58())
                .not("state", "in", "(VERIFIED_FAIL,CANCELLED)")
                .maybeSingle()

            if (existingSession) {
                // Re-return the same transaction without creating a duplicate session
                // The escrow PDA is deterministic so the tx is the same
            } else {
                // Record the trade session
                await supabase.from("trade_sessions").insert({
                    listing_id: listingId,
                    buyer_wallet: buyerPubkey.toBase58(),
                    seller_wallet: listing.seller_wallet,
                    state: "ESCROW_FUNDED",
                })
            }

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
