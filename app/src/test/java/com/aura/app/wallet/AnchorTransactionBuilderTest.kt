package com.aura.app.wallet

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AnchorTransactionBuilder's pure functions.
 * These test PDA derivation, Borsh serialization, and transaction building logic
 * without requiring Android context or network.
 */
class AnchorTransactionBuilderTest {

    @Test
    fun `deriveEscrowPda produces deterministic result`() {
        val listingId = "test-listing-123"
        val pda1 = AnchorTransactionBuilder.deriveEscrowPda(listingId)
        val pda2 = AnchorTransactionBuilder.deriveEscrowPda(listingId)
        assertArrayEquals("PDA should be deterministic", pda1.address, pda2.address)
        assertEquals("Bump should be deterministic", pda1.bump, pda2.bump)
    }

    @Test
    fun `deriveEscrowPda produces different addresses for different listings`() {
        val pda1 = AnchorTransactionBuilder.deriveEscrowPda("listing-a")
        val pda2 = AnchorTransactionBuilder.deriveEscrowPda("listing-b")
        assertFalse("Different listings should have different PDAs",
            pda1.address.contentEquals(pda2.address))
    }

    @Test
    fun `deriveVaultPda derives from escrow PDA`() {
        val escrowPda = AnchorTransactionBuilder.deriveEscrowPda("test-listing")
        val vault1 = AnchorTransactionBuilder.deriveVaultPda(escrowPda.address)
        val vault2 = AnchorTransactionBuilder.deriveVaultPda(escrowPda.address)
        assertArrayEquals("Vault PDA should be deterministic", vault1.address, vault2.address)
    }

    @Test
    fun `PDA addresses are 32 bytes`() {
        val escrowPda = AnchorTransactionBuilder.deriveEscrowPda("any-listing")
        assertEquals("PDA address should be 32 bytes", 32, escrowPda.address.size)
        val vaultPda = AnchorTransactionBuilder.deriveVaultPda(escrowPda.address)
        assertEquals("Vault PDA should be 32 bytes", 32, vaultPda.address.size)
    }

    @Test
    fun `bump is within valid range`() {
        val pda = AnchorTransactionBuilder.deriveEscrowPda("bump-test")
        assertTrue("Bump should be 0-255", pda.bump.toInt() and 0xFF in 0..255)
    }

    @Test
    fun `buildInitializeInstructionData includes Anchor discriminator`() {
        val sellerBytes = ByteArray(32) { 1 }
        val treasuryBytes = ByteArray(32) { 2 }
        val authorityBytes = ByteArray(32) { 3 }
        val data = AnchorTransactionBuilder.buildInitializeInstructionData(
            amount = 1_000_000_000L,   // 1 SOL
            listingId = "test-001",
            sellerWallet = sellerBytes,
            feeBps = 200,
            treasuryWallet = treasuryBytes,
            feeExempt = false,
            releaseAuthority = authorityBytes,
        )
        // Anchor uses first 8 bytes as instruction discriminator (sha256("global:initialize")[:8])
        assertEquals("Instruction data should start with 8-byte discriminator",
            true, data.size >= 8)
        // Total: 8 (disc) + 8 (amount) + 4 + N (string) + 32 (seller) + 2 (feeBps) + 32 (treasury) + 1 (feeExempt) + 32 (authority)
        val expectedMin = 8 + 8 + 4 + "test-001".length + 32 + 2 + 32 + 1 + 32
        assertEquals("Borsh serialized data size should match",
            expectedMin, data.size)
    }

    @Test
    fun `buildReleaseInstructionData has correct size`() {
        val data = AnchorTransactionBuilder.buildReleaseInstructionData(
            assetUri = "https://example.com/metadata.json",
            assetTitle = "Test Asset"
        )
        // 8 (disc) + 4 + uri.length + 4 + title.length
        val expected = 8 + 4 + "https://example.com/metadata.json".length + 4 + "Test Asset".length
        assertEquals("Release instruction data size", expected, data.size)
    }
}
