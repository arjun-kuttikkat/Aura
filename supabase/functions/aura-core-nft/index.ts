// supabase/functions/aura-core-nft/index.ts
// Dynamic Core NFTs — mints and evolves on-chain reputation badges
// Seed (0-7 days) → Sprout (8-30) → Tree (31-90) → Aura (90+)

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { createUmi } from "https://esm.sh/@metaplex-foundation/umi-bundle-defaults"
import { generateSigner, keypairIdentity, publicKey } from "https://esm.sh/@metaplex-foundation/umi"
import {
    createV1,
    updateV1,
    fetchAssetV1,
} from "https://esm.sh/@metaplex-foundation/mpl-core"

const SOLANA_RPC = Deno.env.get("SOLANA_RPC_URL") || "https://api.devnet.solana.com"
const AUTHORITY_KEY = JSON.parse(Deno.env.get("SOLANA_AUTHORITY_KEY") || "[]")
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!
const SUPABASE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
}

function getEvolutionStage(streakDays: number): { name: string; level: number; uri: string } {
    if (streakDays >= 90) return { name: "Aura", level: 4, uri: "https://aura.so/nft/aura.json" }
    if (streakDays >= 31) return { name: "Tree", level: 3, uri: "https://aura.so/nft/tree.json" }
    if (streakDays >= 8) return { name: "Sprout", level: 2, uri: "https://aura.so/nft/sprout.json" }
    return { name: "Seed", level: 1, uri: "https://aura.so/nft/seed.json" }
}

serve(async (req: Request) => {
    if (req.method === "OPTIONS") {
        return new Response(null, { headers: corsHeaders })
    }

    try {
        const { walletAddress, action } = await req.json()

        if (!walletAddress) {
            return new Response(JSON.stringify({ error: "walletAddress required" }), {
                status: 400,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            })
        }

        // Authorization: Verify the caller owns this wallet via JWT
        const authHeader = req.headers.get("Authorization")
        if (!authHeader || !authHeader.startsWith("Bearer ")) {
            return new Response(JSON.stringify({ error: "Authorization header required" }), {
                status: 401,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            })
        }

        // Decode JWT to extract wallet_address claim (verified by Supabase gateway)
        const token = authHeader.replace("Bearer ", "")
        const payloadB64 = token.split(".")[1]
        const payload = JSON.parse(atob(payloadB64))
        const jwtWallet = payload.wallet_address || payload.app_metadata?.wallet_address

        if (jwtWallet !== walletAddress) {
            return new Response(JSON.stringify({ error: "Wallet address does not match authenticated user" }), {
                status: 403,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            })
        }

        const supabase = createClient(SUPABASE_URL, SUPABASE_KEY)
        const umi = createUmi(SOLANA_RPC)

        // Load authority keypair
        const authorityKeypair = umi.eddsa.createKeypairFromSecretKey(new Uint8Array(AUTHORITY_KEY))
        umi.use(keypairIdentity(authorityKeypair))

        // Fetch user profile to get streak data
        const { data: profile } = await supabase
            .from("profiles")
            .select("*")
            .eq("wallet_address", walletAddress)
            .single()

        if (!profile) {
            return new Response(JSON.stringify({ error: "Profile not found" }), {
                status: 404,
                headers: { ...corsHeaders, "Content-Type": "application/json" },
            })
        }

        const stage = getEvolutionStage(profile.streak_days || 0)

        if (action === "mint") {
            // Mint a new Core NFT for first-time users
            const assetSigner = generateSigner(umi)

            await createV1(umi, {
                asset: assetSigner,
                name: `Aura Badge: ${stage.name}`,
                uri: stage.uri,
                owner: publicKey(walletAddress),
            }).sendAndConfirm(umi)

            // Store NFT address in profile
            await supabase
                .from("profiles")
                .update({ nft_mint: assetSigner.publicKey.toString() })
                .eq("wallet_address", walletAddress)

            return new Response(
                JSON.stringify({
                    success: true,
                    action: "mint",
                    nftAddress: assetSigner.publicKey.toString(),
                    stage: stage.name,
                    level: stage.level,
                }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            )
        }

        if (action === "evolve") {
            // Update existing Core NFT metadata based on new streak
            const nftMint = profile.nft_mint

            if (!nftMint) {
                return new Response(JSON.stringify({ error: "No NFT found. Mint first." }), {
                    status: 400,
                    headers: { ...corsHeaders, "Content-Type": "application/json" },
                })
            }

            await updateV1(umi, {
                asset: publicKey(nftMint),
                name: `Aura Badge: ${stage.name}`,
                uri: stage.uri,
            }).sendAndConfirm(umi)

            return new Response(
                JSON.stringify({
                    success: true,
                    action: "evolve",
                    nftAddress: nftMint,
                    stage: stage.name,
                    level: stage.level,
                }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            )
        }

        return new Response(JSON.stringify({ error: "Invalid action. Use 'mint' or 'evolve'." }), {
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
