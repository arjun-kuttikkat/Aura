import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createUmi } from "npm:@metaplex-foundation/umi-bundle-defaults";
import { createV1, mplCore } from "npm:@metaplex-foundation/mpl-core";
import { keypairIdentity, generateSigner, publicKey } from "npm:@metaplex-foundation/umi";
import bs58 from "npm:bs58";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * Mints two receipt NFTs (one to buyer, one to seller) after escrow release.
 * Both parties get an on-chain proof of the completed trade.
 */
serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }

    try {
        const {
            tradeId,
            listingId,
            buyerWallet,
            sellerWallet,
            listingTitle,
            amountLamports,
            releaseTxSignature,
        } = await req.json();

        if (!tradeId || !listingId || !buyerWallet || !sellerWallet) {
            throw new Error("tradeId, listingId, buyerWallet, and sellerWallet are required");
        }

        const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
        const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
        const supabase = createClient(supabaseUrl, supabaseKey);
        const receiptMetadataUri = `${supabaseUrl}/functions/v1/receipt-metadata`;

        // Verify trade session is in a completed/released state
        const { data: session } = await supabase
            .from('trade_sessions')
            .select('id, state, buyer_wallet, seller_wallet')
            .eq('id', tradeId)
            .single();

        if (!session || session.buyer_wallet !== buyerWallet || session.seller_wallet !== sellerWallet) {
            return new Response(JSON.stringify({ error: 'Invalid trade session or wallet mismatch.' }), {
                status: 403,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        const rpcUrl = Deno.env.get("HELIUS_RPC_URL");
        if (!rpcUrl) throw new Error("mint-receipt-nft: HELIUS_RPC_URL secret not set. Add it in Supabase Dashboard → Edge Functions → Secrets.");
        const umi = createUmi(rpcUrl).use(mplCore());

        const secretKeyStr = Deno.env.get("SOLANA_AUTHORITY_KEY");
        if (!secretKeyStr) throw new Error("mint-receipt-nft: SOLANA_AUTHORITY_KEY secret not set. Add it in Supabase Dashboard → Edge Functions → Secrets.");
        const keypair = umi.eddsa.createKeypairFromSecretKey(bs58.decode(secretKeyStr));
        umi.use(keypairIdentity(keypair));

        const title = (listingTitle || "Aura Trade").slice(0, 32);
        const receiptName = `Aura Receipt: ${title}`;

        // Mint receipt to buyer (with retry for transient RPC errors)
        const buyerAsset = generateSigner(umi);
        let lastErr: unknown = null;
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                await createV1(umi, {
                    asset: buyerAsset,
                    name: receiptName,
                    uri: receiptMetadataUri,
                    owner: publicKey(buyerWallet),
                }).sendAndConfirm(umi);
                break;
            } catch (e) {
                lastErr = e;
                if (attempt < 2) await new Promise(r => setTimeout(r, 1000 * (attempt + 1)));
            }
        }
        if (lastErr) throw lastErr;

        // Mint receipt to seller
        const sellerAsset = generateSigner(umi);
        lastErr = null;
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                await createV1(umi, {
                    asset: sellerAsset,
                    name: receiptName,
                    uri: receiptMetadataUri,
                    owner: publicKey(sellerWallet),
                }).sendAndConfirm(umi);
                break;
            } catch (e) {
                lastErr = e;
                if (attempt < 2) await new Promise(r => setTimeout(r, 1000 * (attempt + 1)));
            }
        }
        if (lastErr) throw lastErr;

        const buyerMint = buyerAsset.publicKey.toString();
        const sellerMint = sellerAsset.publicKey.toString();

        await supabase
            .from('trade_sessions')
            .update({
                receipt_mint_buyer: buyerMint,
                receipt_mint_seller: sellerMint,
            })
            .eq('id', tradeId);

        return new Response(JSON.stringify({
            success: true,
            receiptMintBuyer: buyerMint,
            receiptMintSeller: sellerMint,
            message: "Receipt NFTs minted to both parties",
        }), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
    } catch (err: any) {
        return new Response(JSON.stringify({ error: err.message }), {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
    }
});
