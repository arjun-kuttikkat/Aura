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
        // Level 1: Under 50, basic robot avatar (Low streak)
        { wallet_address: "Newbie_111...", aura_score: 30, streak_days: 2, last_scan_at: new Date().toISOString() },
        { wallet_address: "Beginner_22...", aura_score: 45, streak_days: 5, last_scan_at: new Date().toISOString() },
        { wallet_address: "Learner_333...", aura_score: 49, streak_days: 12, last_scan_at: new Date().toISOString() },

        // Level "Sprout" -> >8
        { wallet_address: "Growing_444...", aura_score: 55, streak_days: 15, last_scan_at: new Date().toISOString() },
        { wallet_address: "MidTrader_5...", aura_score: 60, streak_days: 20, last_scan_at: new Date().toISOString() },
        // Level "Tree" -> >31
        { wallet_address: "SolidSeller_6...", aura_score: 74, streak_days: 35, last_scan_at: new Date().toISOString() },

        // Level MAX: 75+, aura_score 90+ (High streak >= 90 Aura ✨) Box avatar
        { wallet_address: "ElitePro_77...", aura_score: 79, streak_days: 40, last_scan_at: new Date().toISOString() },
        { wallet_address: "TrustGod_88...", aura_score: 85, streak_days: 60, last_scan_at: new Date().toISOString() },
        { wallet_address: "AuraKing_99...", aura_score: 96, streak_days: 95, last_scan_at: new Date().toISOString() },
        { wallet_address: "FinalBoss_0...", aura_score: 100, streak_days: 125, last_scan_at: new Date().toISOString() },
    ];

    console.log("Seeding 10 fake profiles...");
    const profiles = [];
    for (const s of sellers) {
        const res = await fetch(`${supabaseUrl}/rest/v1/profiles?on_conflict=wallet_address`, {
            method: 'POST',
            headers: { ...headers, 'Prefer': 'resolution=merge-duplicates,return=representation' },
            body: JSON.stringify(s)
        });
        const data = await res.json();
        if (data && data.length > 0) {
            profiles.push(data[0]);
            console.log(`Created Profile: ${data[0].wallet_address} (Aura: ${data[0].aura_score}, Streak: ${data[0].streak_days})`);
        }
    }

    // Default H3 index for San Francisco testing area so it appears on screen
    const defaultH3 = "89283082803ffff";

    // 2. Create Listings
    const listings = [
        { seller_wallet: sellers[0].wallet_address, title: "Used Mechanical Keyboard", price_lamports: 500_000_000, condition: "Fair", minted_status: "PENDING", fingerprint_hash: "hash1", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[1].wallet_address, title: "AirPods Pro Gen 2", price_lamports: 800_000_000, condition: "Good", minted_status: "MINTED", fingerprint_hash: "hash2", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[2].wallet_address, title: "Vintage Leather Jacket", price_lamports: 1_200_000_000, condition: "Used", minted_status: "VERIFIED", fingerprint_hash: "hash3", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[3].wallet_address, title: "Fender Stratocaster", price_lamports: 5_000_000_000, condition: "Great", minted_status: "MINTED", fingerprint_hash: "hash4", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[4].wallet_address, title: "Sony A7III Camera Body", price_lamports: 8_500_000_000, condition: "Like New", minted_status: "VERIFIED", fingerprint_hash: "hash5", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[5].wallet_address, title: "Herman Miller Chair", price_lamports: 3_000_000_000, condition: "Good", minted_status: "VERIFIED", fingerprint_hash: "hash6", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[6].wallet_address, title: "Yeezy Boost 350", price_lamports: 1_500_000_000, condition: "Deadstock", minted_status: "VERIFIED", fingerprint_hash: "hash7", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[7].wallet_address, title: "MacBook Pro M3 Max", price_lamports: 18_000_000_000, condition: "Mint", minted_status: "VERIFIED", fingerprint_hash: "hash8", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[8].wallet_address, title: "Audemars Piguet Royal Oak", price_lamports: 150_000_000_000, condition: "Mint", minted_status: "VERIFIED", fingerprint_hash: "hash9", created_at: Date.now(), h3_index: defaultH3 },
        { seller_wallet: sellers[9].wallet_address, title: "Porsche 911 GT3 Allocation", price_lamports: 500_000_000_000, condition: "New", minted_status: "VERIFIED", fingerprint_hash: "hash10", created_at: Date.now(), h3_index: defaultH3 },
    ];

    console.log("Seeding 10 fake listings (all mapped to localized San Francisco Hotzone: 89283082803ffff)...");
    for (const l of listings) {
        const data = await doPost('listings', l);
        if (data && data.length > 0) {
            console.log(`Created Listing: ${data[0].title}`);
        }
    }

    console.log("Network Seed Completed Successfully.");
}

main().catch(console.error);
