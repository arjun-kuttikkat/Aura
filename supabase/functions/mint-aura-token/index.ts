// supabase/functions/mint-aura-token/index.ts
// $AURA SPL Token — Tap-to-Earn tokenomics
// Mints reward tokens to buyer and seller wallets on verified trade completion

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import {
    Connection,
    PublicKey,
    Keypair,
    Transaction,
} from "https://esm.sh/@solana/web3.js@1"
import {
    createMintToInstruction,
    getAssociatedTokenAddress,
    createAssociatedTokenAccountInstruction,
} from "https://esm.sh/@solana/spl-token"

const SOLANA_RPC = Deno.env.get("SOLANA_RPC_URL") || "https://api.devnet.solana.com"
const AUTHORITY_KEY = JSON.parse(Deno.env.get("SOLANA_AUTHORITY_KEY") || "[]")
const AURA_MINT = Deno.env.get("AURA_TOKEN_MINT") || ""
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!
const SUPABASE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!

const TRADE_REWARD = 10_000_000  // 10 $AURA (6 decimals)
const CHECK_REWARD = 1_000_000   //  1 $AURA per daily check

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
}

serve(async (req: Request) => {
    if (req.method === "OPTIONS") {
        return new Response(null, { headers: corsHeaders })
    }

    try {
        const { action, buyerWallet, sellerWallet, walletAddress, streakMultiplier } = await req.json()

        const connection = new Connection(SOLANA_RPC)
        const authority = Keypair.fromSecretKey(new Uint8Array(AUTHORITY_KEY))
        const mintPubkey = new PublicKey(AURA_MINT)

        const supabase = createClient(SUPABASE_URL, SUPABASE_KEY)

        if (action === "trade_reward") {
            // Airdrop $AURA to both buyer and seller for completing a verified trade
            if (!buyerWallet || !sellerWallet) {
                return new Response(JSON.stringify({ error: "buyerWallet and sellerWallet required" }), {
                    status: 400,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }

            const buyer = new PublicKey(buyerWallet)
            const seller = new PublicKey(sellerWallet)

            const tx = new Transaction()

            // Create ATAs if they don't exist, then mint to both
            for (const wallet of [buyer, seller]) {
                const ata = await getAssociatedTokenAddress(mintPubkey, wallet)
                const ataInfo = await connection.getAccountInfo(ata)

                if (!ataInfo) {
                    tx.add(
                        createAssociatedTokenAccountInstruction(
                            authority.publicKey, ata, wallet, mintPubkey
                        )
                    )
                }

                tx.add(
                    createMintToInstruction(
                        mintPubkey, ata, authority.publicKey, TRADE_REWARD
                    )
                )
            }

            const latestBlockhash = await connection.getLatestBlockhash()
            tx.recentBlockhash = latestBlockhash.blockhash
            tx.feePayer = authority.publicKey
            tx.sign(authority)

            const sig = await connection.sendRawTransaction(tx.serialize())
            await connection.confirmTransaction(sig)

            return new Response(
                JSON.stringify({
                    success: true,
                    action: "trade_reward",
                    signature: sig,
                    buyerReward: TRADE_REWARD,
                    sellerReward: TRADE_REWARD,
                }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            )
        }

        if (action === "check_reward") {
            // Airdrop $AURA for daily Aura Check with streak multiplier
            if (!walletAddress) {
                return new Response(JSON.stringify({ error: "walletAddress required" }), {
                    status: 400,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }

            const wallet = new PublicKey(walletAddress)
            const multiplier = Math.min(streakMultiplier || 1, 10) // Cap at 10x
            const reward = CHECK_REWARD * multiplier

            const ata = await getAssociatedTokenAddress(mintPubkey, wallet)
            const tx = new Transaction()

            const ataInfo = await connection.getAccountInfo(ata)
            if (!ataInfo) {
                tx.add(
                    createAssociatedTokenAccountInstruction(
                        authority.publicKey, ata, wallet, mintPubkey
                    )
                )
            }

            tx.add(createMintToInstruction(mintPubkey, ata, authority.publicKey, reward))

            const latestBlockhash = await connection.getLatestBlockhash()
            tx.recentBlockhash = latestBlockhash.blockhash
            tx.feePayer = authority.publicKey
            tx.sign(authority)

            const sig = await connection.sendRawTransaction(tx.serialize())
            await connection.confirmTransaction(sig)

            return new Response(
                JSON.stringify({
                    success: true,
                    action: "check_reward",
                    signature: sig,
                    reward: reward,
                    multiplier: multiplier,
                }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            )
        }

        return new Response(JSON.stringify({ error: "Invalid action" }), {
            status: 400,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
        })
    } catch (e) {
        return new Response(JSON.stringify({ error: e.message }), {
            status: 500,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
        })
    }
})
