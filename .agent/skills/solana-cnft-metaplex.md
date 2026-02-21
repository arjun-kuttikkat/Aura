---
name: Metaplex Compressed NFTs for Visual Fingerprints
description: How to mint and query cNFTs for physical items using Metaplex and Digital Asset RPC APIs.
---

# Compressed NFTs (cNFTs) for Aura

## 1. Digital Asset Standard (DAS)
Use DAS API methods via an RPC provider (like Helius) to query cNFTs, since they do not exist in standard account space.
- POST to RPC with `getAsset`
- POST to RPC with `getAssetsByOwner`

## 2. Minting UI Flow & Visual Fingerprints
- User scans a physical item (Rolex, MacBook).
- Upload the image/fingerprint metadata to decentralized storage (Arweave/IPFS).
- Build the `MintToCollectionV1` instruction for Bubblegum.
- Execute via MWA.
- Display the result using a `LazyVerticalGrid` in Compose.
