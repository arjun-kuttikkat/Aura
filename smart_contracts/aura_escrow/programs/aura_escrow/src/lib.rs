use anchor_lang::prelude::*;
use anchor_lang::system_program;

declare_id!("AuRAEscrow1111111111111111111111111111111111");

#[program]
pub mod aura_escrow {
    use super::*;

    pub fn initialize(ctx: Context<Initialize>, amount: u64, listing_id: String, seller_wallet: Pubkey) -> Result<()> {
        let escrow = &mut ctx.accounts.escrow_pda;
        escrow.buyer = ctx.accounts.buyer.key();
        escrow.seller = seller_wallet;
        escrow.listing_id = listing_id.clone();
        escrow.amount = amount;
        escrow.is_released = false;

        let cpi_context = CpiContext::new(
            ctx.accounts.system_program.to_account_info(),
            system_program::Transfer {
                from: ctx.accounts.buyer.to_account_info(),
                to: ctx.accounts.vault_pda.to_account_info(),
            },
        );
        system_program::transfer(cpi_context, amount)?;

        Ok(())
    }

    pub fn release_funds_and_mint(ctx: Context<ReleaseFunds>, _asset_uri: String, _asset_title: String) -> Result<()> {
        let escrow = &mut ctx.accounts.escrow_pda;
        require!(!escrow.is_released, EscrowError::AlreadyReleased);
        require!(
            escrow.seller == ctx.accounts.seller.key(),
            EscrowError::UnauthorizedSeller
        );

        let amount = escrow.amount;
        let vault = &ctx.accounts.vault_pda;

        let escrow_key = escrow.key();
        let vault_bump = ctx.bumps.vault_pda;
        let seeds = &[
            b"vault",
            escrow_key.as_ref(),
            &[vault_bump],
        ];
        let signer = &[&seeds[..]];

        let cpi_context = CpiContext::new_with_signer(
            ctx.accounts.system_program.to_account_info(),
            system_program::Transfer {
                from: vault.to_account_info(),
                to: ctx.accounts.seller.to_account_info(),
            },
            signer,
        );
        system_program::transfer(cpi_context, amount)?;

        escrow.is_released = true;

        Ok(())
    }
}

#[derive(Accounts)]
#[instruction(amount: u64, listing_id: String, seller_wallet: Pubkey)]
pub struct Initialize<'info> {
    #[account(mut)]
    pub buyer: Signer<'info>,

    #[account(
        init,
        payer = buyer,
        space = 8 + 32 + 32 + 4 + listing_id.len() + 8 + 1,
        seeds = [b"escrow", listing_id.as_bytes()],
        bump
    )]
    pub escrow_pda: Account<'info, EscrowState>,

    #[account(
        mut,
        seeds = [b"vault", escrow_pda.key().as_ref()],
        bump
    )]
    /// CHECK: Vault is a PDA storing SOL securely
    pub vault_pda: AccountInfo<'info>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ReleaseFunds<'info> {
    #[account(mut)]
    /// CHECK: The seller receiving the funds — verified against escrow_pda.seller
    pub seller: AccountInfo<'info>,

    #[account(
        mut,
        seeds = [b"escrow", escrow_pda.listing_id.as_bytes()],
        bump,
        constraint = escrow_pda.seller == seller.key() @ EscrowError::UnauthorizedSeller,
    )]
    pub escrow_pda: Account<'info, EscrowState>,

    #[account(
        mut,
        seeds = [b"vault", escrow_pda.key().as_ref()],
        bump
    )]
    /// CHECK: Vault PDA
    pub vault_pda: AccountInfo<'info>,

    pub system_program: Program<'info, System>,
}

#[account]
pub struct EscrowState {
    pub buyer: Pubkey,
    pub seller: Pubkey,
    pub listing_id: String,
    pub amount: u64,
    pub is_released: bool,
}

#[error_code]
pub enum EscrowError {
    #[msg("Escrow has already been released")]
    AlreadyReleased,
    #[msg("Unauthorized: seller does not match escrow record")]
    UnauthorizedSeller,
}
