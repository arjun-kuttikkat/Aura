use anchor_lang::prelude::*;

declare_id!("AuRAVaULtXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

#[program]
pub mod aura_escrow {
    use super::*;

    /// Buyer locks funds in the Escrow PDA.
    pub fn initialize(ctx: Context<Initialize>, listing_id: String, amount: u64) -> Result<()> {
        let escrow = &mut ctx.accounts.escrow;
        escrow.buyer = ctx.accounts.buyer.key();
        escrow.seller = ctx.accounts.seller.key();
        escrow.authority = ctx.accounts.authority.key();
        escrow.amount = amount;
        escrow.listing_id = listing_id;
        escrow.vault_bump = ctx.bumps.vault;

        // Transfer SOL from buyer to PDA vault
        let transfer_instruction = anchor_lang::solana_program::system_instruction::transfer(
            &ctx.accounts.buyer.key(),
            &ctx.accounts.vault.key(),
            amount,
        );
        anchor_lang::solana_program::program::invoke(
            &transfer_instruction,
            &[
                ctx.accounts.buyer.to_account_info(),
                ctx.accounts.vault.to_account_info(),
                ctx.accounts.system_program.to_account_info(),
            ],
        )?;

        Ok(())
    }

    /// Backend authority releases funds to the seller after NFC validation.
    pub fn release_funds(ctx: Context<ReleaseEscrow>) -> Result<()> {
        let escrow = &mut ctx.accounts.escrow;
        let amount = escrow.amount;

        let seeds = &[
            b"vault",
            escrow.to_account_info().key.as_ref(),
            &[escrow.vault_bump],
        ];
        let signer = &[&seeds[..]];

        // Transfer SOL from PDA vault to seller
        let transfer_instruction = anchor_lang::solana_program::system_instruction::transfer(
            &ctx.accounts.vault.key(),
            &ctx.accounts.seller.key(),
            amount,
        );

        anchor_lang::solana_program::program::invoke_signed(
            &transfer_instruction,
            &[
                ctx.accounts.vault.to_account_info(),
                ctx.accounts.seller.to_account_info(),
                ctx.accounts.system_program.to_account_info(),
            ],
            signer,
        )?;

        // Close the escrow account and return lamports to buyer
        let dest_starting_lamports = ctx.accounts.buyer.lamports();
        **ctx.accounts.buyer.lamports.borrow_mut() = dest_starting_lamports.checked_add(escrow.to_account_info().lamports()).unwrap();
        **escrow.to_account_info().lamports.borrow_mut() = 0;

        Ok(())
    }
}

#[derive(Accounts)]
#[instruction(listing_id: String)]
pub struct Initialize<'info> {
    #[account(
        init,
        payer = buyer,
        space = 8 + 32 + 32 + 32 + 8 + 4 + listing_id.len() + 1,
        seeds = [b"escrow", listing_id.as_bytes()],
        bump
    )]
    pub escrow: Account<'info, EscrowAccount>,

    #[account(mut)]
    pub buyer: Signer<'info>,

    /// CHECK: Safe, just storing pubkey for the seller destination
    pub seller: AccountInfo<'info>,
    
    /// CHECK: Safe, just storing pubkey for the backend oracle
    pub authority: AccountInfo<'info>,

    #[account(
        mut,
        seeds = [b"vault", escrow.key().as_ref()],
        bump
    )]
    pub vault: SystemAccount<'info>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ReleaseEscrow<'info> {
    #[account(mut, has_one = seller, has_one = authority)]
    pub escrow: Account<'info, EscrowAccount>,
    
    #[account(mut)]
    pub seller: SystemAccount<'info>,
    
    /// CHECK: We are returning the rent strictly to the buyer
    #[account(mut)]
    pub buyer: SystemAccount<'info>,

    #[account(mut, seeds = [b"vault", escrow.key().as_ref()], bump = escrow.vault_bump)]
    pub vault: SystemAccount<'info>,

    // MANDATORY SIGNER: Only the backend oracle can execute this instruction
    pub authority: Signer<'info>,

    pub system_program: Program<'info, System>,
}

#[account]
pub struct EscrowAccount {
    pub buyer: Pubkey,
    pub seller: Pubkey,
    pub authority: Pubkey,
    pub amount: u64,
    pub listing_id: String,
    pub vault_bump: u8,
}
