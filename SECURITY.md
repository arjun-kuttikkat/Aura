# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability, please follow these steps:

1. **Do NOT** open a public issue
2. Email security details to: **arjun@auramkt.xyz**
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

## Response Timeline

- We will acknowledge receipt within **48 hours**
- We will provide an initial assessment within **7 days**
- We will keep you informed of our progress
- We will notify you when the vulnerability is fixed

## Aura-Specific Security Considerations

### Wallet & Funds
- SOL is held in an **Anchor PDA vault** — only the program can release funds
- Escrow release requires server-side verification of NFC cryptographic proof
- Private keys **never** leave the user's wallet app (Phantom / Solflare)
- The app uses Mobile Wallet Adapter (MWA) — signing happens in the wallet, not in Aura

### NFC Verification
- NTAG 424 DNA tags use **AES-128-CMAC** with counter-bound session keys
- SUN (Secure Unique NFC) URLs include an encrypted file data counter preventing replay attacks
- The master AES key is stored **only** in the Edge Function environment, never on the client

### Database & Backend
- All Supabase tables enforce **Row Level Security (RLS)** using JWT `wallet_address` claims
- The `requesting_wallet()` helper extracts the wallet address from `auth.jwt()` with strict validation
- No table uses a permissive `USING (true)` policy for write operations
- Edge Functions validate all inputs before interacting with Solana or the database

### Build Security
- **R8 full mode** is enabled for release builds with aggressive shrinking and obfuscation
- All API keys are stored in `local.properties` (git-ignored) and injected via `BuildConfig`
- Helius RPC endpoint is proxied through an Edge Function — the raw API key is never exposed to the client
- ProGuard rules explicitly keep cryptographic classes (Bouncy Castle) and HCE entry points

## Security Best Practices for Users

- Keep your Android OS updated to the latest security patch
- Use a hardware-backed wallet or a reputable wallet app with biometric lock
- **Never** share your private keys, seed phrases, or recovery words
- Verify transaction details in your wallet app before signing
- Only install Aura from official sources (GitHub releases or Google Play)
- Meet in public, well-lit locations for physical handovers
- Ensure secure communication with blockchain networks
- Follow Android security best practices for data storage

Thank you for helping keep Aura secure!
