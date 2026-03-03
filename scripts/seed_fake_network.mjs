import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Helper to wait
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

async function main() {
    console.log("Reading credentials from local.properties...");
    const propsPath = path.resolve(__dirname, '../local.properties');
    if (!fs.existsSync(propsPath)) {
        console.error("local.properties not found at", propsPath);
        process.exit(1);
    }

    const props = fs.readFileSync(propsPath, 'utf8');
    const supabaseUrl = props.split('\n').find(l => l.startsWith('SUPABASE_URL='))?.split('=')[1]?.replace(/["']/g, '').trim();
    const supabaseKey = props.split('\n').find(l => l.startsWith('SUPABASE_KEY='))?.split('=')[1]?.replace(/["']/g, '').trim();

    if (!supabaseUrl || !supabaseKey) {
        console.error("SUPABASE_URL or SUPABASE_KEY missing in local.properties");
        process.exit(1);
    }

    console.log("Credentials loaded successfully.");

    const headers = {
        'apikey': supabaseKey,
        'Authorization': `Bearer ${supabaseKey}`,
        'Content-Type': 'application/json',
        'Prefer': 'return=representation'
    };

    const doPost = async (table, payload) => {
        const res = await fetch(`${supabaseUrl}/rest/v1/${table}`, {
            method: 'POST',
            headers,
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const err = await res.text();
            console.error(`Failed to insert into ${table}:`, err);
            return null;
        }
        return await res.json();
    };

    // 1. Create Sellers
    const sellers = [
        {
            wallet_address: "EliteVendor_x9f...",
            aura_score: 95,
            streak_days: 120,
            last_scan_at: new Date().toISOString()
        },
        {
            wallet_address: "SneakerHead_42...",
            aura_score: 45,
            streak_days: 12,
            last_scan_at: new Date().toISOString()
        },
        {
            wallet_address: "RiskyTrader_00...",
            aura_score: 5,
            streak_days: 1,
            last_scan_at: new Date().toISOString()
        }
    ];

    console.log("Seeding fake profiles...");
    const profiles = [];
    for (const s of sellers) {
        // Upsert style: just create if they don't exist. To keep it simple, we use ignore conflicts
        const res = await fetch(`${supabaseUrl}/rest/v1/profiles?on_conflict=wallet_address`, {
            method: 'POST',
            headers: { ...headers, 'Prefer': 'resolution=merge-duplicates,return=representation' },
            body: JSON.stringify(s)
        });
        const data = await res.json();
        if (data && data.length > 0) {
            profiles.push(data[0]);
            console.log(`Created Profile: ${data[0].wallet_address} (Aura: ${data[0].aura_score})`);
        }
    }

    if (profiles.length < 3) {
        console.error("Failed to seed profiles. Ensure RLS allows inserts or use service role key.");
        // Still proceed, just in case they were created.
    }

    // Default H3 index for San Francisco testing area so it appears on screen (Assuming user is simulated there or global)
    // We will just leave h3_zones null so they show up globally. 

    // 2. Create Listings
    const listings = [
        {
            seller_wallet: sellers[0].wallet_address,
            title: "Rolex Submariner Date (Mint)",
            price_lamports: 15_000_000_000, // 15 SOL
            condition: "Like New",
            minted_status: "VERIFIED",
            fingerprint_hash: "hash_rolex_8932jfd",
            created_at: Date.now()
        },
        {
            seller_wallet: sellers[1].wallet_address,
            title: "Nike Air Jordan 1 Retro (Chicago)",
            price_lamports: 2_500_000_000, // 2.5 SOL
            condition: "Good",
            minted_status: "MINTED",
            fingerprint_hash: "hash_jordan_111",
            created_at: Date.now() - 100000
        },
        {
            seller_wallet: sellers[2].wallet_address,
            title: "PS5 Console - Unopened",
            price_lamports: 3_000_000_000, // 3 SOL
            condition: "New",
            minted_status: "PENDING",
            fingerprint_hash: "hash_ps5_000",
            created_at: Date.now() - 500000
        }
    ];

    console.log("Seeding fake listings...");
    const createdListings = [];
    for (const l of listings) {
        const data = await doPost('listings', l);
        if (data && data.length > 0) {
            createdListings.push(data[0]);
            console.log(`Created Listing: ${data[0].title}`);
        }
    }

    console.log("Network Seed Completed Successfully.");
}

main().catch(console.error);
