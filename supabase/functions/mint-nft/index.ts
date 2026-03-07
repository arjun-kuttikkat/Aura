import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createUmi } from "npm:@metaplex-foundation/umi-bundle-defaults";
import { createV1, mplCore } from "npm:@metaplex-foundation/mpl-core";
import { keypairIdentity, generateSigner, publicKey } from "npm:@metaplex-foundation/umi";
import bs58 from "npm:bs58";
import { createClient } from "npm:@supabase/supabase-js";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }

    try {
        const { listingId, title, sellerWalletBase58, metadataUrl } = await req.json();

        if (!listingId || !sellerWalletBase58) {
            throw new Error("listingId and sellerWalletBase58 are required");
        }

        // Authorization: Verify an active trade session exists in VERIFIED/NFC_VERIFIED state
        const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
        const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
        const supabase = createClient(supabaseUrl, supabaseKey);

        const { data: session, error: sessionErr } = await supabase
            .from('trade_sessions')
            .select('id, state, seller_wallet')
            .eq('listing_id', listingId)
            .in('state', ['NFC_VERIFIED', 'ITEM_VERIFIED', 'ESCROW_RELEASED'])
            .order('created_at', { ascending: false })
            .limit(1)
            .single();

        if (sessionErr || !session) {
            return new Response(JSON.stringify({ error: 'No verified trade session found for this listing. Complete NFC verification first.' }), {
                status: 403,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        if (session.seller_wallet !== sellerWalletBase58) {
            return new Response(JSON.stringify({ error: 'Seller wallet does not match trade session.' }), {
                status: 403,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }
        // 1. Umi Setup for Solana Mainnet via Helius
        const rpcUrl = Deno.env.get("HELIUS_RPC_URL");
        if (!rpcUrl) throw new Error("HELIUS_RPC_URL is required — set in Supabase Edge Function secrets");
        const umi = createUmi(rpcUrl).use(mplCore());

        // 2. Identity Setup (Aura's Oracle Payer Wallet)
        const secretKeyStr = Deno.env.get("SOLANA_AUTHORITY_KEY");
        if (!secretKeyStr) throw new Error("Backend Authority Key missing");

        const keypair = umi.eddsa.createKeypairFromSecretKey(bs58.decode(secretKeyStr));
        umi.use(keypairIdentity(keypair));

        // 3. Define the Core Asset
        const asset = generateSigner(umi);
        const sellerPubkey = publicKey(sellerWalletBase58);

        // 4. Minting the Metaplex Core NFT
        const tx = await createV1(umi, {
            asset,
            name: `Aura Verified: ${title}`,
            uri: metadataUrl,                     // Link to Arweave or Supabase Storage JSON
            owner: sellerPubkey,                  // Send directly to the Seller's wallet
        }).sendAndConfirm(umi);

        // 5. Update Supabase with Mint Address
        const mintAddressBase58 = asset.publicKey.toString();

        const { error: dbError } = await supabase
            .from('marketplace_listings')
            .update({ mint_address: mintAddressBase58 })
            .eq('id', listingId);

        if (dbError) throw dbError;

        return new Response(JSON.stringify({
            success: true,
            mintAddress: mintAddressBase58,
            message: "NFT Minted to Seller Wallet successfully"
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
