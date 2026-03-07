use anchor_lang::prelude::*;
use anchor_lang::system_program;
use anchor_lang::solana_program::{program::invoke_signed, system_instruction};

declare_id!("BMKWLYrXtuuxp4TA4yNhrs9LbomR1fMdbrko6R7Qj5WM");

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

        if ctx.accounts.vault_pda.lamports() == 0 {
            let rent_lamports = Rent::get()?.minimum_balance(0);
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
}

#[derive(Accounts)]
#[instruction(amount: u64, listing_id: String, seller_wallet: Pubkey, fee_bps: u16, treasury_wallet: Pubkey, fee_exempt: bool)]
pub struct Initialize<'info> {
    #[account(mut)]
    pub buyer: Signer<'info>,

    #[account(
        init,
        payer = buyer,
        space = 8 + 32 + 32 + 4 + listing_id.len() + 8 + 1 + 2 + 32 + 1,
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
}

#[error_code]
pub enum EscrowError {
    #[msg("Escrow has already been released")]
    AlreadyReleased,
    #[msg("Unauthorized: seller does not match escrow record")]
    UnauthorizedSeller,
    #[msg("Unauthorized treasury account")]
    UnauthorizedTreasury,
    #[msg("Math overflow")]
    MathOverflow,
}
