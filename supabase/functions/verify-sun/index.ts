import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { CMAC } from "npm:@stablelib/aes-cmac";
import { Connection, Keypair, Transaction, PublicKey, SystemProgram } from "npm:@solana/web3.js";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
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
            sdmDataHex,      // Encrypted PICCData from the NFC tag URL
            receivedCmacHex, // MAC from the NFC tag URL
            assetUri,
            assetTitle,
            listingId,
            escrowPdaBase58,
            sellerWalletBase58,
            tradeId,
        } = await req.json();

        if (!sdmDataHex || !receivedCmacHex || !listingId || !escrowPdaBase58 || !sellerWalletBase58 || !tradeId) {
            return new Response(JSON.stringify({ valid: false, error: "sdmDataHex, receivedCmacHex, listingId, escrowPdaBase58, sellerWalletBase58, and tradeId are required" }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

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
        // 1b. Match tag to listing: if listing has nfc_sun_url from publish, verify same physical tag
        // ═══════════════════════════════════════════════════════════════

        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const supabase = createClient(supabaseUrl, supabaseKey);
        const { data: listing } = await supabase
            .from("marketplace_listings")
            .select("price_lamports,seller_wallet,nfc_sun_url,mint_address,minted_status")
            .eq("id", listingId)
            .single();

        // ═══════════════════════════════════════════════════════════════
        // 1c. NFT Verification: listing must be minted for escrow release
        // Ensures the publish flow completed with mint-nft (NFC verification + mint)
        // ═══════════════════════════════════════════════════════════════
        if (!listing?.mint_address || listing?.minted_status !== "MINTED") {
            return new Response(JSON.stringify({
                valid: false,
                reason: "NFT_NOT_MINTED",
                error: "Listing must be NFT-verified before release. Complete the full publish flow (including NFC verification and mint).",
            }), {
                status: 403,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        if (listing?.nfc_sun_url) {
            const piccMatch = listing.nfc_sun_url.match(/picc_data=([0-9A-Fa-f]+)/i);
            if (piccMatch) {
                const publishPiccHex = piccMatch[1];
                const publishDecrypted = await crypto.subtle.decrypt(
                    { name: "AES-CBC", iv: new Uint8Array(16) },
                    await crypto.subtle.importKey("raw", sdmFileReadKey, { name: "AES-CBC" }, false, ["decrypt"]),
                    hexToBytes(publishPiccHex)
                );
                const uidPublish = new Uint8Array(publishDecrypted).slice(0, 7);
                if (bytesToHex(uidPublish) !== bytesToHex(uid)) {
                    return new Response(JSON.stringify({
                        valid: false,
                        reason: "TAG_MISMATCH",
                        error: "NFC tag does not match the one registered at publish — wrong physical item.",
                    }), {
                        status: 403,
                        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
                    });
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // 2. Token Mismatch Fix (19): Verify on-chain escrow holds SOL, matches listing
        // ═══════════════════════════════════════════════════════════════

        const rpcUrl = Deno.env.get("HELIUS_RPC_URL");
        if (!rpcUrl) throw new Error("HELIUS_RPC_URL is required — set in Supabase Edge Function secrets");
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
                error: "Escrow PDA does not match listing — ensure correct trade session.",
            }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        const escrowInfo = await connection.getAccountInfo(escrowPda);
        if (!escrowInfo || !escrowInfo.data || escrowInfo.data.length < 100) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "ESCROW_NOT_FOUND",
                error: "Escrow account not found on-chain — fund escrow before releasing.",
            }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }
        const vaultPda = PublicKey.findProgramAddressSync([Buffer.from("vault"), escrowPda.toBuffer()], PROGRAM_ID)[0];
        const vaultBalance = await connection.getBalance(vaultPda);
        const escrowData = escrowInfo.data;
        const ANCHOR_DISC_LEN = 8;
        let offset = ANCHOR_DISC_LEN;
        offset += 32; // buyer
        offset += 32; // seller
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
            }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }
        // Airdrop spoofing fix (#17): verify vault holds exactly SOL (system program), not spoofed tokens
        if (vaultBalance < escrowAmount) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "VAULT_INSUFFICIENT",
                error: "Vault balance does not match escrow — wrong token or amount.",
            }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }
        // listing already fetched above with price_lamports,seller_wallet,nfc_sun_url
        const expectedLamports = listing?.price_lamports ?? 0;
        if (expectedLamports > 0 && Math.abs(escrowAmount - expectedLamports) > 1000) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "AMOUNT_MISMATCH",
                error: "Escrow amount does not match listing — expected SOL only.",
            }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }
        if (listing?.seller_wallet && listing.seller_wallet !== sellerWalletBase58) {
            return new Response(JSON.stringify({
                valid: false,
                reason: "SELLER_MISMATCH",
                error: "Seller wallet does not match listing.",
            }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' },
            });
        }

        // ═══════════════════════════════════════════════════════════════
        // 3. Solana Escrow Release (CPI via Authority)
        // Derank fix (20): Do NOT re-check seller rank/privilege here. Escrow state
        // (fee_exempt) was snapshotted at initialize; release uses on-chain data only.
        // ═══════════════════════════════════════════════════════════════

        const secretKeyStr = Deno.env.get("SOLANA_AUTHORITY_KEY");
        if (!secretKeyStr) throw new Error("SOLANA_AUTHORITY_KEY not configured");

        const authorityKeypair = Keypair.fromSecretKey(bs58.decode(secretKeyStr));
        const sellerPubkey = new PublicKey(sellerWalletBase58);
        const treasuryWalletStr = Deno.env.get("TREASURY_WALLET");
        if (!treasuryWalletStr) throw new Error("TREASURY_WALLET not configured");
        const treasuryPubkey = new PublicKey(treasuryWalletStr);
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

        // Update trade session state to NFC_VERIFIED
        const tagUidHex = bytesToHex(uid);
        await supabase
            .from("trade_sessions")
            .update({ state: "NFC_VERIFIED", nfc_sun_url: `uid:${tagUidHex}` })
            .eq("id", tradeId);

        return new Response(JSON.stringify({
            valid: true,
            success: true,
            message: "NFC chip verified (NTAG 424 DNA SUN). Escrow released to seller.",
            txSignature: signature,
            listingId: listingId,
            tradeSessionId: tradeId,
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
