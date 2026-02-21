---
name: Face-to-Face Connection via NFC & Solana Pay
description: How to exchange connection and transaction details using Android NFC and Solana Pay URIs.
---

# NFC & Solana Pay Connection

## 1. Android NFC Reader Mode
Implement `NfcAdapter.ReaderCallback` in the Android Activity or handle NFC intents to listen for other Seekers tapping.

## 2. Solana Pay URIs
Format the payment or connection payload according to the Solana Pay specification:
`solana:<recipient>?amount=<amount>&reference=<reference>&label=<label>&message=<message>`

## 3. The "Tap" Workflow
- Seller displays intent to sell.
- Apps transmit the Solana Pay request via an NFC NDEF message.
- Buyer's phone parses the URI, fetches the transaction details, and prompts the MWA for signing.
