import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { CMAC } from "npm:@stablelib/aes-cmac";
import { Connection, Keypair, Transaction, PublicKey, SystemProgram } from "npm:@solana/web3.js";
import bs58 from "npm:bs58";

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

/**
 * NXP NTAG 424 DNA SUN (Secure Unique NFC) Verification + Solana Escrow Release
 *
 * Protocol:
 * 1. Decrypt picc_data with SDMMetaReadKey to recover {UID (7 bytes), SDMReadCtr (3 bytes)}
 * 2. Derive session SDMMAC key: AES-CMAC(SDM_FILE_READ_KEY, sv2 = 0x3CC300 || SDMReadCtr || 0x0000...)
 * 3. Compute expected MAC: AES-CMAC(session_key, "" ), truncate to even-indexed bytes
 * 4. Compare against the cmac URL parameter from the chip's SUN URL
 */

function hexToBytes(hex: string): Uint8Array {
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < bytes.length; i++) {
        bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
    }
    return bytes;
}

function bytesToHex(bytes: Uint8Array): string {
    return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Decrypt picc_data (AES-128-CBC with zero IV) to recover UID + ReadCounter.
 * picc_data = AES-128-CBC(SDMMetaReadKey, UID || ReadCtr || ...padding)
 * The first 16 bytes of picc_data decrypt to: UID(7) || ReadCtr(3) || padding(6)
 */
function decryptPiccData(encPiccDataHex: string, sdmMetaReadKeyHex: string): { uid: Uint8Array; readCtr: Uint8Array } {
    // Use Web Crypto-compatible AES-CBC decrypt with zero IV
    const key = hexToBytes(sdmMetaReadKeyHex);
    const data = hexToBytes(encPiccDataHex);
    
    // Manual AES-128-CBC decrypt with zero IV using the CMAC library's underlying AES
    // For NTAG 424 DNA, picc_data is exactly 16 bytes (one AES block)
    // Decrypt = AES_ECB_Decrypt(key, ciphertext) XOR IV (IV=0 so just decrypt)
    const cmacInstance = new CMAC(key);
    // We need raw AES decrypt. CMAC gives us AES encrypt internally.
    // Alternative: use picc_data as-is if the tag provides it decrypted in the URL.
    // In the SUN message, picc_data is the encrypted block. We use AES-CBC with IV=0.
    
    // For production: use native crypto for AES-CBC decrypt
    // Since Deno supports Web Crypto, we'll handle this in the main handler
    return { uid: data.slice(0, 7), readCtr: data.slice(7, 10) };
}

/**
 * Derive the SUN MAC session key per NXP AN12196:
 * SessionSDMMACKey = AES-CMAC(SDMFileReadKey, SV2)
 * SV2 = 0x3C || 0xC3 || 0x00 || 0x01 || 0x00 || 0x80 || ReadCtr(3 LE) || 0x00...0x00 (pad to 16)
 */
function deriveSessionMacKey(sdmFileReadKey: Uint8Array, readCtr: Uint8Array): Uint8Array {
    // Build SV2 (Session Vector 2) - 16 bytes
    const sv2 = new Uint8Array(16);
    sv2[0] = 0x3C;
    sv2[1] = 0xC3;
    sv2[2] = 0x00;
    sv2[3] = 0x01;
    sv2[4] = 0x00;
    sv2[5] = 0x80;
    // ReadCtr is 3 bytes, little-endian
    sv2[6] = readCtr[0];
    sv2[7] = readCtr[1];
    sv2[8] = readCtr[2];
    // Remaining bytes are 0x00 (already initialized)
    
    const cmac = new CMAC(sdmFileReadKey);
    cmac.update(sv2);
    return cmac.digest();
}

/**
 * Compute SUN MAC and truncate per NXP spec:
 * FullMAC = AES-CMAC(SessionSDMMACKey, inputData)
 * TruncatedMAC = bytes at even indices [0,2,4,6,8,10,12,14] = 8 bytes
 */
function computeSunMac(sessionKey: Uint8Array, inputData: Uint8Array): Uint8Array {
    const cmac = new CMAC(sessionKey);
    cmac.update(inputData);
    const fullMac = cmac.digest();
    
    // NXP truncation: take bytes at even indices (0, 2, 4, 6, 8, 10, 12, 14)
    const truncated = new Uint8Array(8);
    for (let i = 0; i < 8; i++) {
        truncated[i] = fullMac[i * 2];
    }
    return truncated;
}

serve(async (req) => {
    if (req.method === 'OPTIONS') {
        return new Response('ok', { headers: corsHeaders });
    }

    try {
        const {
            listingId,
            sdmDataHex,      // Encrypted PICCData from the NFC tag URL
            receivedCmacHex, // MAC from the NFC tag URL
            escrowPdaBase58,
            sellerWalletBase58,
            buyerWalletBase58,
            assetUri,
            assetTitle,
            amount
        } = await req.json();

        // ═══════════════════════════════════════════════════════════════
        // 1. NFC NTAG 424 DNA SUN Verification
        // ═══════════════════════════════════════════════════════════════

        const sdmFileReadKeyHex = Deno.env.get("NFC_MASTER_AES_KEY");
        if (!sdmFileReadKeyHex) throw new Error("NFC_MASTER_AES_KEY not configured in Vault");
        const sdmFileReadKey = hexToBytes(sdmFileReadKeyHex);

        // Step 1: Decrypt picc_data to recover UID and ReadCounter
        // For NTAG 424 DNA, sdmDataHex contains the encrypted PICCData (32 hex chars = 16 bytes)
        const encPiccData = hexToBytes(sdmDataHex);

        // AES-128-CBC decrypt with zero IV using Web Crypto
        const cryptoKey = await crypto.subtle.importKey(
            "raw", sdmFileReadKey, { name: "AES-CBC" }, false, ["decrypt"]
        );
        const zeroIV = new Uint8Array(16);
        const decryptedBuffer = await crypto.subtle.decrypt(
            { name: "AES-CBC", iv: zeroIV },
            cryptoKey,
            encPiccData
        );
        const decrypted = new Uint8Array(decryptedBuffer);
        // Decrypted: UID(7 bytes) || ReadCtr(3 bytes LE) || padding
        const uid = decrypted.slice(0, 7);
        const readCtr = decrypted.slice(7, 10);

        // Step 2: Derive session MAC key
        const sessionKey = deriveSessionMacKey(sdmFileReadKey, readCtr);

        // Step 3: Compute expected SUN MAC
        // Input to MAC is empty for SDMENCFileData=00, or file data if configured.
        // Standard NTAG 424 DNA SUN: MAC input is empty when no SDMENCFileData is set.
        const macInput = new Uint8Array(0);
        const expectedMac = computeSunMac(sessionKey, macInput);
        const expectedMacHex = bytesToHex(expectedMac);

        // Step 4: Compare
        const receivedCmacClean = receivedCmacHex.toLowerCase().trim();
        const isValid = expectedMacHex === receivedCmacClean;

        if (!isValid) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "MAC_MISMATCH",
                error: "NFC tag authentication failed — potential counterfeit detected.",
                expected: expectedMacHex,
                received: receivedCmacClean,
                uidHex: bytesToHex(uid),
                readCounter: readCtr[0] | (readCtr[1] << 8) | (readCtr[2] << 16),
            }), {
                status: 403,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        // ═══════════════════════════════════════════════════════════════
        // 2. Solana Escrow Release (CPI via Authority)
        // ═══════════════════════════════════════════════════════════════

        const rpcUrl = Deno.env.get("HELIUS_RPC_URL");
        if (!rpcUrl) throw new Error("HELIUS_RPC_URL is required — set in Supabase Edge Function secrets");
        const connection = new Connection(rpcUrl, "confirmed");

        const secretKeyStr = Deno.env.get("SOLANA_AUTHORITY_KEY");
        if (!secretKeyStr) throw new Error("SOLANA_AUTHORITY_KEY not configured");

        const authorityKeypair = Keypair.fromSecretKey(bs58.decode(secretKeyStr));
        const escrowPda = new PublicKey(escrowPdaBase58);
        const sellerPubkey = new PublicKey(sellerWalletBase58);
        const treasuryWalletBase58 = Deno.env.get("TREASURY_WALLET");
        if (!treasuryWalletBase58) throw new Error("TREASURY_WALLET not configured");
        const treasuryPubkey = new PublicKey(treasuryWalletBase58);
        const PROGRAM_ID = new PublicKey("BMKWLYrXtuuxp4TA4yNhrs9LbomR1fMdbrko6R7Qj5WM");
        const vaultPda = PublicKey.findProgramAddressSync([Buffer.from("vault"), escrowPda.toBuffer()], PROGRAM_ID)[0];

        // Build release_funds_and_mint instruction with Borsh-encoded args
        const crypto_mod = await import("node:crypto");
        const discriminator = crypto_mod.createHash("sha256")
            .update("global:release_funds_and_mint")
            .digest()
            .slice(0, 8);

        // Borsh-encode: [8 disc][4+N asset_uri string][4+N asset_title string]
        const uriBytes = Buffer.from(assetUri || "");
        const titleBytes = Buffer.from(assetTitle || "");
        const dataLen = 8 + 4 + uriBytes.length + 4 + titleBytes.length;
        const instructionData = Buffer.alloc(dataLen);
        let offset = 0;
        discriminator.copy(instructionData, offset); offset += 8;
        instructionData.writeUInt32LE(uriBytes.length, offset); offset += 4;
        uriBytes.copy(instructionData, offset); offset += uriBytes.length;
        instructionData.writeUInt32LE(titleBytes.length, offset); offset += 4;
        titleBytes.copy(instructionData, offset);

        const tx = new Transaction();
        tx.add({
            programId: PROGRAM_ID,
            keys: [
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

        return new Response(JSON.stringify({
            valid: true,
            success: true,
            message: "NFC chip verified (NTAG 424 DNA SUN). Escrow released to seller.",
            txSignature: signature,
            uidHex: bytesToHex(uid),
            readCounter: readCtr[0] | (readCtr[1] << 8) | (readCtr[2] << 16),
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
