import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { Connection } from "npm:@solana/web3.js";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const PROMOTE_FEE_LAMPORTS = 10 * 1_000_000_000; // 10 SOL

serve(async (req) => {
    if (req.method === "OPTIONS") {
        return new Response("ok", { headers: corsHeaders });
    }

    try {
        const { listingId, txSignature, sellerWallet, useAuraPoints } = await req.json();

        if (!listingId || !sellerWallet) {
            return new Response(
                JSON.stringify({ error: "listingId and sellerWallet are required" }),
                { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // Aura points path: 50 points for 24h (client deducts locally)
        if (useAuraPoints === true) {
            const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
            const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
            const supabase = createClient(supabaseUrl, supabaseKey);

            const { data: listing, error: fetchError } = await supabase
                .from("marketplace_listings")
                .select("seller_wallet")
                .eq("id", listingId)
                .single();

            if (fetchError || !listing) {
                return new Response(
                    JSON.stringify({ error: "Listing not found" }),
                    { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
                );
            }

            if (listing.seller_wallet !== sellerWallet) {
                return new Response(
                    JSON.stringify({ error: "Only the seller can promote this listing" }),
                    { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
                );
            }

            const promotedUntil = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
            const promotedAt = new Date().toISOString();

            const { error: updateError } = await supabase
                .from("marketplace_listings")
                .update({
                    is_promoted: true,
                    promoted_at: promotedAt,
                    promoted_until: promotedUntil,
                })
                .eq("id", listingId);

            if (updateError) {
                return new Response(
                    JSON.stringify({ error: updateError.message }),
                    { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
                );
            }

            return new Response(
                JSON.stringify({
                    success: true,
                    message: "Listing promoted for 24 hours (50 Aura points)",
                    promoted_until: promotedUntil,
                }),
                { headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // SOL path (legacy)
        if (!txSignature) {
            return new Response(
                JSON.stringify({ error: "txSignature is required when not using Aura points" }),
                { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        const rpcUrl = Deno.env.get("HELIUS_RPC_URL");
        if (!rpcUrl) throw new Error("HELIUS_RPC_URL is required");
        const treasuryWallet = Deno.env.get("TREASURY_WALLET");
        if (!treasuryWallet) throw new Error("TREASURY_WALLET is required");

        const connection = new Connection(rpcUrl, "confirmed");

        // 1. Fetch transaction
        const tx = await connection.getParsedTransaction(txSignature, {
            maxSupportedTransactionVersion: 0,
        });

        if (!tx || !tx.meta) {
            return new Response(
                JSON.stringify({ error: "Transaction not found or invalid" }),
                { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        if (tx.meta.err) {
            return new Response(
                JSON.stringify({ error: "Transaction failed on-chain" }),
                { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // 2. Find SOL transfer from seller -> treasury for 10 SOL
        const signerIndex = tx.transaction.message.accountKeys.findIndex(
            (k) => "pubkey" in k && k.pubkey.toBase58() === sellerWallet
        );
        if (signerIndex < 0) {
            return new Response(
                JSON.stringify({ error: "Transaction was not signed by the seller wallet" }),
                { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        let foundValidTransfer = false;
        const instructions = tx.transaction.message.instructions;
        for (const ix of instructions) {
            if ("parsed" in ix && ix.parsed) {
                const p = ix.parsed as { type?: string; info?: { lamports?: number; source?: string; destination?: string } };
                if (p.type === "transfer" && p.info) {
                    const lamports = p.info.lamports ?? 0;
                    const dest = p.info.destination;
                    const src = p.info.source;
                    if (
                        lamports >= PROMOTE_FEE_LAMPORTS &&
                        dest === treasuryWallet &&
                        src === sellerWallet
                    ) {
                        foundValidTransfer = true;
                        break;
                    }
                }
            }
        }

        if (!foundValidTransfer) {
            return new Response(
                JSON.stringify({
                    error: `Transaction must transfer at least 10 SOL from seller to treasury. Expected ${PROMOTE_FEE_LAMPORTS} lamports to ${treasuryWallet}`,
                }),
                { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // 3. Verify listing exists and seller matches
        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const supabase = createClient(supabaseUrl, supabaseKey);

        const { data: listing, error: fetchError } = await supabase
            .from("marketplace_listings")
            .select("seller_wallet")
            .eq("id", listingId)
            .single();

        if (fetchError || !listing) {
            return new Response(
                JSON.stringify({ error: "Listing not found" }),
                { status: 404, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        if (listing.seller_wallet !== sellerWallet) {
            return new Response(
                JSON.stringify({ error: "Only the seller can promote this listing" }),
                { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        // 4. Update listing
        const promotedUntil = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
        const promotedAt = new Date().toISOString();

        const { error: updateError } = await supabase
            .from("marketplace_listings")
            .update({
                is_promoted: true,
                promoted_at: promotedAt,
                promoted_until: promotedUntil,
            })
            .eq("id", listingId);

        if (updateError) {
            return new Response(
                JSON.stringify({ error: updateError.message }),
                { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
            );
        }

        return new Response(
            JSON.stringify({
                success: true,
                message: "Listing promoted for 24 hours",
                promoted_until: promotedUntil,
            }),
            { headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
    } catch (err: unknown) {
        const message = err instanceof Error ? err.message : "Unknown error";
        return new Response(
            JSON.stringify({ error: message }),
            { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
    }
});
