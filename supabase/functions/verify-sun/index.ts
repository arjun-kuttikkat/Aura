import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { CMAC } from "npm:@stablelib/aes-cmac";
import { Connection, Keypair, Transaction, PublicKey, SystemProgram } from "npm:@solana/web3.js";
import bs58 from "npm:bs58";

// Ensure CORS
const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }

    try {
        const { listingId, sdmDataHex, receivedCmacHex, escrowPdaBase58, sellerWalletBase58, amount } = await req.json();

        // 1. NFC Cryptographic Verification
        // The master AES key for the physical tags (stored securely in Supabase Vault)
        const masterAppKeyHex = Deno.env.get("NFC_MASTER_AES_KEY");
        if (!masterAppKeyHex) throw new Error("NFC Master Key not configured");

        const appKey = new Uint8Array(Buffer.from(masterAppKeyHex, 'hex'));
        const sdmData = new Uint8Array(Buffer.from(sdmDataHex, 'hex'));
        const receivedCmac = new Uint8Array(Buffer.from(receivedCmacHex, 'hex'));

        const cmac = new CMAC(appKey);
        cmac.update(sdmData);
        const fullCmac = cmac.digest();

        // NTAG 424 DNA uses the heavily truncated 8-byte left-most CMAC
        const truncatedCmac = fullCmac.slice(0, 8);
        const isValid = truncatedCmac.every((val, i) => val === receivedCmac[i]);

        if (!isValid) {
            return new Response(JSON.stringify({ error: "Invalid NFC Signature. Potential physical spoofing detected." }), {
                status: 403,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        // 2. Solana Smart Contract Execution (Release Funds)
        const rpcUrl = Deno.env.get("HELIUS_RPC_URL") || "https://api.mainnet-beta.solana.com";
        const connection = new Connection(rpcUrl, "confirmed");

        const secretKeyStr = Deno.env.get("SOLANA_AUTHORITY_KEY");
        if (!secretKeyStr) throw new Error("Backend Authority missing");

        const authorityKeypair = Keypair.fromSecretKey(bs58.decode(secretKeyStr));
        const escrowPda = new PublicKey(escrowPdaBase58);
        const sellerPubkey = new PublicKey(sellerWalletBase58);
        const PROGRAM_ID = new PublicKey("BMKWLYrXtuuxp4TA4yNhrs9LbomR1fMdbrko6R7Qj5WM");
        const vaultPda = PublicKey.findProgramAddressSync([Buffer.from("vault"), escrowPda.toBuffer()], PROGRAM_ID)[0];

        // Build release_funds_and_mint instruction
        // Anchor discriminator for "global:release_funds_and_mint"
        const crypto = await import("node:crypto");
        const discriminator = crypto.createHash("sha256")
            .update("global:release_funds_and_mint")
            .digest()
            .slice(0, 8);

        const tx = new Transaction();
        tx.add({
            programId: PROGRAM_ID,
            keys: [
                { pubkey: sellerPubkey, isSigner: false, isWritable: true },
                { pubkey: escrowPda, isSigner: false, isWritable: true },
                { pubkey: vaultPda, isSigner: false, isWritable: true },
                { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
            ],
            data: discriminator,
        });

        tx.feePayer = authorityKeypair.publicKey;
        tx.recentBlockhash = (await connection.getLatestBlockhash()).blockhash;
        tx.sign(authorityKeypair);

        const signature = await connection.sendRawTransaction(tx.serialize());
        await connection.confirmTransaction(signature, "confirmed");

        return new Response(JSON.stringify({
            success: true,
            message: "NFC Verified and Escrow successfully released",
            txSignature: signature
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
