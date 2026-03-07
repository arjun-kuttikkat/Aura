use anchor_lang::prelude::*;
use anchor_lang::system_program;
use anchor_lang::solana_program::{program::invoke_signed, system_instruction};

declare_id!("BMKWLYrXtuuxp4TA4yNhrs9LbomR1fMdbrko6R7Qj5WM");

/// Hostage exploit mitigation: buyer can refund after REFUND_TIMEOUT_SECS if seller never released.
const REFUND_TIMEOUT_SECS: i64 = 24 * 60 * 60; // 24 hours

#[program]
pub mod aura_escrow {
    use super::*;

    pub fn initialize(
        ctx: Context<Initialize>,
        amount: u64,
        listing_id: String,
        seller_wallet: Pubkey,
        fee_bps: u16,
        treasury_wallet: Pubkey,
        fee_exempt: bool,
        release_authority: Pubkey,
    ) -> Result<()> {
        let escrow = &mut ctx.accounts.escrow_pda;
        escrow.buyer = ctx.accounts.buyer.key();
        escrow.seller = seller_wallet;
        escrow.listing_id = listing_id.clone();
        escrow.amount = amount;
        escrow.is_released = false;
        escrow.fee_bps = fee_bps;
        escrow.treasury_wallet = treasury_wallet;
        escrow.fee_exempt = fee_exempt;
        escrow.created_at = ctx.clock.unix_timestamp;
        escrow.release_authority = release_authority;

        if ctx.accounts.vault_pda.lamports() == 0 {
            // Rent exemption for 0-byte system account; add buffer for network variations
            let base_rent = Rent::get()?.minimum_balance(0);
            let rent_lamports = base_rent.saturating_add(1_000);
            let escrow_key = escrow.key();
            let vault_bump = ctx.bumps.vault_pda;
            let vault_seeds: &[&[u8]] = &[
                b"vault",
                escrow_key.as_ref(),
                &[vault_bump],
            ];

            let create_ix = system_instruction::create_account(
                &ctx.accounts.buyer.key(),
                &ctx.accounts.vault_pda.key(),
                rent_lamports,
                0,
                &system_program::ID,
            );

            invoke_signed(
                &create_ix,
                &[
                    ctx.accounts.buyer.to_account_info(),
                    ctx.accounts.vault_pda.to_account_info(),
                    ctx.accounts.system_program.to_account_info(),
                ],
                &[vault_seeds],
            )?;
        }

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
        require!(
            escrow.treasury_wallet == ctx.accounts.treasury_wallet.key(),
            EscrowError::UnauthorizedTreasury
        );

        let amount = escrow.amount;
        let vault = &ctx.accounts.vault_pda;
        let fee_amount: u64 = if escrow.fee_exempt {
            0
        } else {
            amount
                .checked_mul(escrow.fee_bps as u64)
                .ok_or(EscrowError::MathOverflow)?
                .checked_div(10_000)
                .ok_or(EscrowError::MathOverflow)?
        };
        let seller_amount = amount
            .checked_sub(fee_amount)
            .ok_or(EscrowError::MathOverflow)?;

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
        system_program::transfer(cpi_context, seller_amount)?;

        if fee_amount > 0 {
            let fee_cpi = CpiContext::new_with_signer(
                ctx.accounts.system_program.to_account_info(),
                system_program::Transfer {
                    from: vault.to_account_info(),
                    to: ctx.accounts.treasury_wallet.to_account_info(),
                },
                signer,
            );
            system_program::transfer(fee_cpi, fee_amount)?;
        }

        escrow.is_released = true;

        Ok(())
    }

    /// Hostage exploit fix: buyer can reclaim SOL if seller never released after REFUND_TIMEOUT_SECS.
    /// Protects buyers when seller ghosts; does not reward hostage-taking (seller should not hand over
    /// item until NFC tap completes).
    pub fn cancel_and_refund(ctx: Context<CancelAndRefund>) -> Result<()> {
        let escrow = &mut ctx.accounts.escrow_pda;
        require!(!escrow.is_released, EscrowError::AlreadyReleased);
        require!(escrow.buyer == ctx.accounts.buyer.key(), EscrowError::UnauthorizedBuyer);

        let now = ctx.clock.unix_timestamp;
        let elapsed = now.saturating_sub(escrow.created_at);
        require!(elapsed >= REFUND_TIMEOUT_SECS, EscrowError::RefundTooEarly);

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
                to: ctx.accounts.buyer.to_account_info(),
            },
            signer,
        );
        // Refund full vault balance (amount + rent) to buyer
        let balance = vault.lamports();
        system_program::transfer(cpi_context, balance)?;

        escrow.is_released = true;

        Ok(())
    }
}

#[derive(Accounts)]
#[instruction(amount: u64, listing_id: String, seller_wallet: Pubkey, fee_bps: u16, treasury_wallet: Pubkey, fee_exempt: bool, release_authority: Pubkey)]
pub struct Initialize<'info> {
    #[account(mut)]
    pub buyer: Signer<'info>,

    #[account(
        init,
        payer = buyer,
        space = 8 + 32 + 32 + 4 + listing_id.len() + 8 + 1 + 2 + 32 + 1 + 8 + 32, // +8 created_at, +32 release_authority
        seeds = [b"escrow", listing_id.as_bytes()],
        bump
    )]
    pub escrow_pda: Account<'info, EscrowState>,

    #[account(
        mut,
        seeds = [b"vault", escrow_pda.key().as_ref()],
        bump
    )]
    /// CHECK: PDA system account used as SOL vault and created on initialize when missing.
    pub vault_pda: AccountInfo<'info>,

    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ReleaseFunds<'info> {
    /// The authorized release signer — must match escrow_pda.release_authority
    pub authority: Signer<'info>,

    #[account(mut)]
    /// CHECK: The seller receiving the funds — verified against escrow_pda.seller
    pub seller: AccountInfo<'info>,

    #[account(
        mut,
        seeds = [b"escrow", escrow_pda.listing_id.as_bytes()],
        bump,
        constraint = escrow_pda.seller == seller.key() @ EscrowError::UnauthorizedSeller,
        constraint = escrow_pda.release_authority == authority.key() @ EscrowError::UnauthorizedAuthority,
    )]
    pub escrow_pda: Account<'info, EscrowState>,

    #[account(
        mut,
        seeds = [b"vault", escrow_pda.key().as_ref()],
        bump
    )]
    /// CHECK: Vault PDA
    pub vault_pda: AccountInfo<'info>,

    #[account(mut)]
    /// CHECK: Platform treasury for marketplace fees, matched against escrow state.
    pub treasury_wallet: AccountInfo<'info>,

    pub system_program: Program<'info, System>,
}

#[account]
pub struct EscrowState {
    pub buyer: Pubkey,
    pub seller: Pubkey,
    pub listing_id: String,
    pub amount: u64,
    pub is_released: bool,
    pub fee_bps: u16,
    pub treasury_wallet: Pubkey,
    pub fee_exempt: bool,
    pub created_at: i64,
    pub release_authority: Pubkey,
}

#[derive(Accounts)]
pub struct CancelAndRefund<'info> {
    #[account(mut)]
    pub buyer: Signer<'info>,

    #[account(
        mut,
        seeds = [b"escrow", escrow_pda.listing_id.as_bytes()],
        bump,
        constraint = escrow_pda.buyer == buyer.key() @ EscrowError::UnauthorizedBuyer,
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

#[error_code]
pub enum EscrowError {
    #[msg("Escrow has already been released")]
    AlreadyReleased,
    #[msg("Only the buyer can cancel and refund")]
    UnauthorizedBuyer,
    #[msg("Refund available only after 24 hours")]
    RefundTooEarly,
    #[msg("Unauthorized: seller does not match escrow record")]
    UnauthorizedSeller,
    #[msg("Unauthorized treasury account")]
    UnauthorizedTreasury,
    #[msg("Unauthorized release authority")]
    UnauthorizedAuthority,
    #[msg("Math overflow")]
    MathOverflow,
}
