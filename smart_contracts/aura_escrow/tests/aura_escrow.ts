import * as anchor from "@coral-xyz/anchor";
import { Program, BN } from "@coral-xyz/anchor";
import { Keypair, PublicKey, SystemProgram, LAMPORTS_PER_SOL } from "@solana/web3.js";
import { assert, expect } from "chai";

// NOTE: This test requires an `anchor test` setup. Tests run against localnet.
// Ensure Anchor.toml is configured and `anchor build` succeeds before running.

describe("aura_escrow", () => {
  const provider = anchor.AnchorProvider.env();
  anchor.setProvider(provider);

  // Load program from workspace
  const program = anchor.workspace.AuraEscrow;

  // Test keypairs
  const buyer = Keypair.generate();
  const seller = Keypair.generate();
  const authority = Keypair.generate();
  const treasury = Keypair.generate();
  const attacker = Keypair.generate();

  const listingId = "test-listing-001";
  const amount = new BN(1 * LAMPORTS_PER_SOL); // 1 SOL
  const feeBps = 200; // 2%

  // Derive PDAs
  let escrowPda: PublicKey;
  let escrowBump: number;
  let vaultPda: PublicKey;
  let vaultBump: number;

  before(async () => {
    // Airdrop SOL to buyer for transactions
    const sig1 = await provider.connection.requestAirdrop(buyer.publicKey, 10 * LAMPORTS_PER_SOL);
    await provider.connection.confirmTransaction(sig1);

    const sig2 = await provider.connection.requestAirdrop(authority.publicKey, 1 * LAMPORTS_PER_SOL);
    await provider.connection.confirmTransaction(sig2);

    const sig3 = await provider.connection.requestAirdrop(attacker.publicKey, 1 * LAMPORTS_PER_SOL);
    await provider.connection.confirmTransaction(sig3);

    // Derive Escrow PDA
    [escrowPda, escrowBump] = PublicKey.findProgramAddressSync(
      [Buffer.from("escrow"), Buffer.from(listingId)],
      program.programId
    );

    // Derive Vault PDA (depends on escrow PDA)
    [vaultPda, vaultBump] = PublicKey.findProgramAddressSync(
      [Buffer.from("vault"), escrowPda.toBuffer()],
      program.programId
    );
  });

  describe("initialize", () => {
    it("should initialize escrow with correct state", async () => {
      await program.methods
        .initialize(
          amount,
          listingId,
          seller.publicKey,
          feeBps,
          treasury.publicKey,
          false, // fee_exempt
          authority.publicKey // release_authority
        )
        .accounts({
          buyer: buyer.publicKey,
          escrowPda,
          vaultPda,
          systemProgram: SystemProgram.programId,
        })
        .signers([buyer])
        .rpc();

      // Verify escrow state
      const escrowAccount = await program.account.escrowState.fetch(escrowPda);
      assert.ok(escrowAccount.buyer.equals(buyer.publicKey));
      assert.ok(escrowAccount.seller.equals(seller.publicKey));
      assert.equal(escrowAccount.listingId, listingId);
      assert.ok(escrowAccount.amount.eq(amount));
      assert.equal(escrowAccount.isReleased, false);
      assert.equal(escrowAccount.feeBps, feeBps);
      assert.ok(escrowAccount.treasuryWallet.equals(treasury.publicKey));
      assert.equal(escrowAccount.feeExempt, false);
      assert.ok(escrowAccount.releaseAuthority.equals(authority.publicKey));
    });

    it("should transfer SOL to vault PDA", async () => {
      const vaultBalance = await provider.connection.getBalance(vaultPda);
      assert.ok(vaultBalance >= amount.toNumber(), "Vault should hold at least the escrow amount");
    });
  });

  describe("release_funds_and_mint", () => {
    it("should reject release from unauthorized signer", async () => {
      try {
        await program.methods
          .releaseFundsAndMint("https://example.com/asset", "Test Asset")
          .accounts({
            authority: attacker.publicKey,
            seller: seller.publicKey,
            escrowPda,
            vaultPda,
            treasuryWallet: treasury.publicKey,
            systemProgram: SystemProgram.programId,
          })
          .signers([attacker])
          .rpc();
        assert.fail("Should have thrown UnauthorizedAuthority error");
      } catch (err: any) {
        assert.include(err.toString(), "UnauthorizedAuthority");
      }
    });

    it("should reject release with wrong seller", async () => {
      const wrongSeller = Keypair.generate();
      try {
        await program.methods
          .releaseFundsAndMint("https://example.com/asset", "Test Asset")
          .accounts({
            authority: authority.publicKey,
            seller: wrongSeller.publicKey,
            escrowPda,
            vaultPda,
            treasuryWallet: treasury.publicKey,
            systemProgram: SystemProgram.programId,
          })
          .signers([authority])
          .rpc();
        assert.fail("Should have thrown UnauthorizedSeller error");
      } catch (err: any) {
        assert.include(err.toString(), "UnauthorizedSeller");
      }
    });

    it("should release funds to seller and fee to treasury", async () => {
      const sellerBalanceBefore = await provider.connection.getBalance(seller.publicKey);
      const treasuryBalanceBefore = await provider.connection.getBalance(treasury.publicKey);

      await program.methods
        .releaseFundsAndMint("https://example.com/asset", "Test Asset")
        .accounts({
          authority: authority.publicKey,
          seller: seller.publicKey,
          escrowPda,
          vaultPda,
          treasuryWallet: treasury.publicKey,
          systemProgram: SystemProgram.programId,
        })
        .signers([authority])
        .rpc();

      const expectedFee = amount.toNumber() * feeBps / 10_000;
      const expectedSeller = amount.toNumber() - expectedFee;

      const sellerBalanceAfter = await provider.connection.getBalance(seller.publicKey);
      const treasuryBalanceAfter = await provider.connection.getBalance(treasury.publicKey);

      assert.equal(
        sellerBalanceAfter - sellerBalanceBefore,
        expectedSeller,
        "Seller should receive amount minus fee"
      );
      assert.equal(
        treasuryBalanceAfter - treasuryBalanceBefore,
        expectedFee,
        "Treasury should receive the fee"
      );

      // Verify escrow is marked as released
      const escrowAccount = await program.account.escrowState.fetch(escrowPda);
      assert.equal(escrowAccount.isReleased, true);
    });

    it("should reject double release", async () => {
      try {
        await program.methods
          .releaseFundsAndMint("https://example.com/asset", "Test Asset")
          .accounts({
            authority: authority.publicKey,
            seller: seller.publicKey,
            escrowPda,
            vaultPda,
            treasuryWallet: treasury.publicKey,
            systemProgram: SystemProgram.programId,
          })
          .signers([authority])
          .rpc();
        assert.fail("Should have thrown AlreadyReleased error");
      } catch (err: any) {
        assert.include(err.toString(), "AlreadyReleased");
      }
    });
  });

  describe("fee-exempt escrow", () => {
    const feeExemptListingId = "test-listing-fee-exempt";
    let feeExemptEscrowPda: PublicKey;
    let feeExemptVaultPda: PublicKey;

    before(async () => {
      [feeExemptEscrowPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("escrow"), Buffer.from(feeExemptListingId)],
        program.programId
      );
      [feeExemptVaultPda] = PublicKey.findProgramAddressSync(
        [Buffer.from("vault"), feeExemptEscrowPda.toBuffer()],
        program.programId
      );
    });

    it("should skip fee for fee-exempt escrow", async () => {
      await program.methods
        .initialize(
          amount,
          feeExemptListingId,
          seller.publicKey,
          feeBps,
          treasury.publicKey,
          true, // fee_exempt = true
          authority.publicKey
        )
        .accounts({
          buyer: buyer.publicKey,
          escrowPda: feeExemptEscrowPda,
          vaultPda: feeExemptVaultPda,
          systemProgram: SystemProgram.programId,
        })
        .signers([buyer])
        .rpc();

      const sellerBalanceBefore = await provider.connection.getBalance(seller.publicKey);
      const treasuryBalanceBefore = await provider.connection.getBalance(treasury.publicKey);

      await program.methods
        .releaseFundsAndMint("https://example.com/asset", "Test Asset")
        .accounts({
          authority: authority.publicKey,
          seller: seller.publicKey,
          escrowPda: feeExemptEscrowPda,
          vaultPda: feeExemptVaultPda,
          treasuryWallet: treasury.publicKey,
          systemProgram: SystemProgram.programId,
        })
        .signers([authority])
        .rpc();

      const sellerBalanceAfter = await provider.connection.getBalance(seller.publicKey);
      const treasuryBalanceAfter = await provider.connection.getBalance(treasury.publicKey);

      // Seller gets full amount, treasury gets nothing
      assert.equal(sellerBalanceAfter - sellerBalanceBefore, amount.toNumber());
      assert.equal(treasuryBalanceAfter - treasuryBalanceBefore, 0);
    });
  });
});
