import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { Connection, Keypair, PublicKey, SystemProgram, Transaction } from "npm:@solana/web3.js";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import bs58 from "npm:bs58";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * release-escrow-photo — Photo verification path for listings WITHOUT NFC at publish.
 *
 * Use when listing has no nfc_sun_url. Verifies item via AI photo match (verify-photo),
 * then releases escrow same as verify-sun.
 *
 * Body: { tradeId, listingId, photoBase64, assetUri, assetTitle, escrowPdaBase58, sellerWalletBase58 }
 */
serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }

    try {
        const {
            tradeId,
            listingId,
            photoBase64,
            assetUri,
            assetTitle,
            escrowPdaBase58,
            sellerWalletBase58,
        } = await req.json();

        if (!tradeId || !listingId || !photoBase64 || !escrowPdaBase58 || !sellerWalletBase58) {
            return new Response(JSON.stringify({
                valid: false,
                error: "tradeId, listingId, photoBase64, escrowPdaBase58, and sellerWalletBase58 are required",
            }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const supabase = createClient(supabaseUrl, supabaseKey);

        // ═══════════════════════════════════════════════════════════════
        // 1. Listing must NOT have nfc_sun_url — this path is for non-NFC listings only
        // ═══════════════════════════════════════════════════════════════

        const { data: listing } = await supabase
            .from("marketplace_listings")
            .select("price_lamports,seller_wallet,nfc_sun_url,mint_address,minted_status,images,title")
            .eq("id", listingId)
            .single();

        if (listing?.nfc_sun_url) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "USE_NFC_PATH",
                error: "Listing has NFC data from publish. Use NFC tap verification (verify-sun) instead.",
            }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        // Log but do not block — receipt NFT will still be minted after release
        if (!listing?.mint_address || listing?.minted_status !== "MINTED") {
            console.warn("Listing not NFT-published; proceeding with release. Receipt NFT will be minted.");
        }

        // ═══════════════════════════════════════════════════════════════
        // 2. AI Photo verification (item_match)
        // ═══════════════════════════════════════════════════════════════

        const verifyRes = await fetch(`${supabaseUrl}/functions/v1/verify-photo`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${supabaseKey}`,
            },
            body: JSON.stringify({
                listingId,
                photoBase64: photoBase64.slice(0, 180000),
                checkType: "item_match",
            }),
        });

        if (!verifyRes.ok) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "VERIFY_PHOTO_FAILED",
                error: "Photo verification service unavailable.",
            }), {
                status: 502,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        const verifyJson = await verifyRes.json();
        if (!verifyJson.pass) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "ITEM_MISMATCH",
                error: verifyJson.feedback || "Item in photo does not match listing.",
            }), {
                status: 403,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        // ═══════════════════════════════════════════════════════════════
        // 3. Verify on-chain escrow (same as verify-sun)
        // ═══════════════════════════════════════════════════════════════

        const rpcUrl = Deno.env.get("HELIUS_RPC_URL");
        if (!rpcUrl) throw new Error("HELIUS_RPC_URL is required");

        const connection = new Connection(rpcUrl, "confirmed");
        const PROGRAM_ID = new PublicKey("BMKWLYrXtuuxp4TA4yNhrs9LbomR1fMdbrko6R7Qj5WM");
        const [escrowPda] = PublicKey.findProgramAddressSync(
            [Buffer.from("escrow"), Buffer.from(listingId)],
            PROGRAM_ID
        );

        if (escrowPda.toBase58() !== escrowPdaBase58) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "ESCROW_MISMATCH",
                error: "Escrow PDA does not match listing.",
            }), { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
        }

        const escrowInfo = await connection.getAccountInfo(escrowPda);
        if (!escrowInfo?.data || escrowInfo.data.length < 100) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "ESCROW_NOT_FOUND",
                error: "Escrow not found on-chain. Fund escrow before releasing.",
            }), { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
        }

        const vaultPda = PublicKey.findProgramAddressSync([Buffer.from("vault"), escrowPda.toBuffer()], PROGRAM_ID)[0];
        const vaultBalance = await connection.getBalance(vaultPda);
        const escrowData = escrowInfo.data;
        let offset = 8 + 32 + 32;
        const listingIdLen = escrowData.readUInt32LE(offset);
        offset += 4 + listingIdLen;
        const escrowAmount = Number(escrowData.readBigUInt64LE(offset));
        offset += 8;
        const isReleased = escrowData.readUInt8(offset) !== 0;

        if (isReleased) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "ALREADY_RELEASED",
                error: "Escrow has already been released.",
            }), { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
        }

        if (vaultBalance < escrowAmount) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "VAULT_INSUFFICIENT",
                error: "Vault balance does not match escrow.",
            }), { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
        }

        const expectedLamports = listing?.price_lamports ?? 0;
        if (expectedLamports > 0 && Math.abs(escrowAmount - expectedLamports) > 1000) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "AMOUNT_MISMATCH",
                error: "Escrow amount does not match listing.",
            }), { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
        }

        if (listing?.seller_wallet && listing.seller_wallet !== sellerWalletBase58) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "SELLER_MISMATCH",
                error: "Seller wallet does not match listing.",
            }), { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
        }

        // ═══════════════════════════════════════════════════════════════
        // 4. Solana Escrow Release (same as verify-sun)
        // ═══════════════════════════════════════════════════════════════

        const crypto_mod = await import("node:crypto");
        const discriminator = crypto_mod.createHash("sha256")
            .update("global:release_funds_and_mint")
            .digest()
            .slice(0, 8);

        const uriBytes = Buffer.from(assetUri || listing?.images?.[0] || "");
        const titleBytes = Buffer.from(assetTitle || listing?.title || "Aura Verified Asset");
        const dataLen = 8 + 4 + uriBytes.length + 4 + titleBytes.length;
        const instructionData = Buffer.alloc(dataLen);
        let off = 0;
        discriminator.copy(instructionData, off); off += 8;
        instructionData.writeUInt32LE(uriBytes.length, off); off += 4;
        uriBytes.copy(instructionData, off); off += uriBytes.length;
        instructionData.writeUInt32LE(titleBytes.length, off); off += 4;
        titleBytes.copy(instructionData, off);

        const secretKeyStr = Deno.env.get("SOLANA_AUTHORITY_KEY");
        if (!secretKeyStr) throw new Error("SOLANA_AUTHORITY_KEY not configured");
        const authorityKeypair = Keypair.fromSecretKey(bs58.decode(secretKeyStr));
        const sellerPubkey = new PublicKey(sellerWalletBase58);
        const treasuryWalletStr = Deno.env.get("TREASURY_WALLET");
        if (!treasuryWalletStr) throw new Error("TREASURY_WALLET not configured");
        const treasuryPubkey = new PublicKey(treasuryWalletStr);

        const tx = new Transaction();
        tx.add({
            programId: PROGRAM_ID,
            keys: [
                { pubkey: authorityKeypair.publicKey, isSigner: true, isWritable: true },
                { pubkey: sellerPubkey, isSigner: false, isWritable: true },
                { pubkey: escrowPda, isSigner: false, isWritable: true },
                { pubkey: vaultPda, isSigner: false, isWritable: true },
                { pubkey: treasuryPubkey, isSigner: false, isWritable: true },
                { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
            ],
            data: instructionData,
        });

        tx.feePayer = authorityKeypair.publicKey;
        tx.recentBlockhash = (await connection.getLatestBlockhash()).blockhash;
        tx.sign(authorityKeypair);

        const signature = await connection.sendRawTransaction(tx.serialize());
        await connection.confirmTransaction(signature, "confirmed");

        const { data: sessionRow } = await supabase
            .from("trade_sessions")
            .select("buyer_wallet")
            .eq("id", tradeId)
            .single();
        await supabase
            .from("trade_sessions")
            .update({ state: "PHOTO_VERIFIED" })
            .eq("id", tradeId);

        let receiptMintBuyer: string | null = null;
        let receiptMintSeller: string | null = null;
        let receiptMintError: string | null = null;
        const mintPayload = JSON.stringify({
            tradeId,
            listingId,
            buyerWallet: sessionRow?.buyer_wallet || "",
            sellerWallet: sellerWalletBase58,
            listingTitle: listing?.title || assetTitle,
            amountLamports: listing?.price_lamports ?? 0,
            releaseTxSignature: signature,
        });
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                const mintRes = await fetch(`${supabaseUrl}/functions/v1/mint-receipt-nft`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json", "Authorization": `Bearer ${supabaseKey}` },
                    body: mintPayload,
                });
                const mintText = await mintRes.text();
                if (mintRes.ok) {
                    const mintJson = JSON.parse(mintText);
                    receiptMintBuyer = mintJson.receiptMintBuyer ?? null;
                    receiptMintSeller = mintJson.receiptMintSeller ?? null;
                    break;
                }
                try {
                    const errJson = JSON.parse(mintText);
                    receiptMintError = errJson?.error || mintText || `HTTP ${mintRes.status}`;
                } catch {
                    receiptMintError = mintText || `HTTP ${mintRes.status}`;
                }
                if (attempt < 2) await new Promise(r => setTimeout(r, 1000 * (attempt + 1)));
            } catch (mintErr: any) {
                receiptMintError = mintErr?.message || String(mintErr);
                if (attempt < 2) await new Promise(r => setTimeout(r, 1000 * (attempt + 1)));
            }
        }

        return new Response(JSON.stringify({
            valid: true,
            success: true,
            message: "Photo verified. Escrow released to seller.",
            txSignature: signature,
            listingId,
            tradeSessionId: tradeId,
            receiptMintBuyer,
            receiptMintSeller,
            receiptMintError: receiptMintError ?? undefined,
        }), {
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
    } catch (err: any) {
        return new Response(JSON.stringify({ valid: false, error: err.message }), {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
    }
});
