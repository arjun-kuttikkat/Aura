package com.aura.app.model

enum class ItemCategory(val label: String, val emoji: String) {
    HAIR       ("Hair",        "💇"),
    OUTFIT     ("Outfit",      "👕"),
    ACCESSORY  ("Accessories", "🎩"),
    BACKGROUND ("Background",  "🌄"),
    EXPRESSION ("Expressions", "😎"),
}

data class StoreItem(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val creditCost: Int,
    val emoji: String,
    val description: String = "",
    // For avatar renderer — which slot/index this maps to
    val avatarSlot: String,
    val slotIndex: Int,
)

// ── Store Catalog (30 items) ───────────────────────────────────────────────────

object StoreCatalog {
    val ALL: List<StoreItem> = listOf(
        // Hair styles (slot: hairStyle)
        StoreItem("h1", "Spiky Fade",       ItemCategory.HAIR,       40,  "⚡", "Bold spiky top with clean fade",   "hairStyle", 1),
        StoreItem("h2", "Braided Crown",     ItemCategory.HAIR,       60,  "👑", "Elegant braided updo",             "hairStyle", 2),
        StoreItem("h3", "Long Waves",        ItemCategory.HAIR,       35,  "🌊", "Flowing shoulder-length waves",    "hairStyle", 3),
        StoreItem("h4", "Bun & Bangs",       ItemCategory.HAIR,       45,  "🎀", "Messy bun with side bangs",        "hairStyle", 4),
        StoreItem("h5", "Mohawk",            ItemCategory.HAIR,       55,  "🦅", "Statement mohawk",                 "hairStyle", 5),
        StoreItem("h6", "Afro",             ItemCategory.HAIR,       50,  "🌸", "Full natural afro",                "hairStyle", 6),
        StoreItem("h7", "Ponytail",          ItemCategory.HAIR,       30,  "🐎", "High slick ponytail",             "hairStyle", 7),

        // Outfits (slot: outfitTop)
        StoreItem("o1", "Hoodie Chill",      ItemCategory.OUTFIT,     50,  "🧥", "Oversized cozy hoodie",           "outfitTop", 1),
        StoreItem("o2", "Street Bomber",     ItemCategory.OUTFIT,     80,  "💣", "Urban bomber jacket",             "outfitTop", 2),
        StoreItem("o3", "Aura Tee",          ItemCategory.OUTFIT,     35,  "✨", "Exclusive Aura branded tee",      "outfitTop", 3),
        StoreItem("o4", "Formal Blazer",     ItemCategory.OUTFIT,     90,  "🎩", "Sleek fitted blazer",             "outfitTop", 4),
        StoreItem("o5", "Tank Top",          ItemCategory.OUTFIT,     25,  "💪", "Clean athletic tank",             "outfitTop", 5),
        StoreItem("o6", "Denim Jacket",      ItemCategory.OUTFIT,     70,  "🔵", "Classic denim jacket",            "outfitTop", 6),
        StoreItem("o7", "Jordan Fit",        ItemCategory.OUTFIT,     120, "👟", "Full Air Jordan drip set",        "outfitTop", 7),

        // Accessories (slots: hat, glasses)
        StoreItem("a1", "Bucket Hat",        ItemCategory.ACCESSORY,  45,  "🪣", "Chill bucket hat",               "hat", 0),
        StoreItem("a2", "Cap Sideways",      ItemCategory.ACCESSORY,  40,  "🧢", "Street snapback cap",            "hat", 1),
        StoreItem("a3", "Flower Crown",      ItemCategory.ACCESSORY,  55,  "🌸", "Cute floral crown",              "hat", 2),
        StoreItem("a4", "Beanie",            ItemCategory.ACCESSORY,  35,  "🧣", "Cozy knit beanie",               "hat", 3),
        StoreItem("a5", "Halo",              ItemCategory.ACCESSORY,  150, "😇", "Rare platinum halo",             "hat", 4),
        StoreItem("a6", "Round Glasses",     ItemCategory.ACCESSORY,  40,  "🔍", "Cool round frames",              "glasses", 0),
        StoreItem("a7", "Aviators",          ItemCategory.ACCESSORY,  55,  "🕶️", "Classic aviator shades",         "glasses", 1),
        StoreItem("a8", "Cat Eye",           ItemCategory.ACCESSORY,  50,  "😺", "Chic cat-eye frames",            "glasses", 2),
        StoreItem("a9", "Cyber Visor",       ItemCategory.ACCESSORY,  100, "🤖", "Futuristic cyber visor",         "glasses", 3),

        // Backgrounds (slot: background)
        StoreItem("b1", "Aurora Night",      ItemCategory.BACKGROUND, 60,  "🌌", "Northern lights sky",            "background", 4),
        StoreItem("b2", "Cherry Blossom",    ItemCategory.BACKGROUND, 55,  "🌸", "Soft pink blossom",              "background", 5),
        StoreItem("b3", "City Neon",         ItemCategory.BACKGROUND, 75,  "🌃", "Cyberpunk neon cityscape",       "background", 6),
        StoreItem("b4", "Golden Hour",       ItemCategory.BACKGROUND, 65,  "🌅", "Warm sunset gradient",           "background", 7),
        StoreItem("b5", "Deep Space",        ItemCategory.BACKGROUND, 80,  "🚀", "Starfield void",                 "background", 8),

        // Expressions (slot: expression)
        StoreItem("e1", "Wink",             ItemCategory.EXPRESSION,  30,  "😉", "Cheeky single wink",             "expression", 1),
        StoreItem("e2", "Cool Smirk",        ItemCategory.EXPRESSION, 45,  "😏", "Confident smirk",                "expression", 2),
        StoreItem("e3", "Surprised",         ItemCategory.EXPRESSION, 25,  "😮", "Wide-eyed surprise",             "expression", 3),
        StoreItem("e4", "Star Eyes",         ItemCategory.EXPRESSION, 80,  "🌟", "Rare sparkle eyes",              "expression", 4),
    )

    fun byCategory(cat: ItemCategory) = ALL.filter { it.category == cat }
}
