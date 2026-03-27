package com.lagradost.quicknovel.ui.foryou.recommendation

import androidx.annotation.Keep

@Keep
enum class TagCategory(val displayName: String) {
    // ============ MAIN GENRES ============
    ACTION("Action"),
    ADVENTURE("Adventure"),
    COMEDY("Comedy"),
    DRAMA("Drama"),
    FANTASY("Fantasy"),
    HORROR("Horror"),
    MYSTERY("Mystery"),
    ROMANCE("Romance"),
    SCI_FI("Sci-Fi"),
    SLICE_OF_LIFE("Slice of Life"),
    THRILLER("Thriller"),
    TRAGEDY("Tragedy"),

    // ============ EASTERN / CULTIVATION ============
    CULTIVATION("Cultivation"),
    WUXIA("Wuxia"),
    XIANXIA("Xianxia"),
    XUANHUAN("Xuanhuan"),
    MURIM("Murim"),
    MARTIAL_ARTS("Martial Arts"),

    // ============ ISEKAI / REINCARNATION ============
    ISEKAI("Isekai"),
    REINCARNATION("Reincarnation"),
    TRANSMIGRATION("Transmigration"),
    REGRESSION("Regression"),
    TIME_LOOP("Time Loop"),

    // ============ LITRPG / SYSTEM ============
    LITRPG("LitRPG"),
    GAMELIT("GameLit"),
    SYSTEM("System"),
    PROGRESSION("Progression"),
    DUNGEON("Dungeon"),
    TOWER("Tower"),

    // ============ MC TYPES (WIZARD FOCUS) ============
    OP_MC("Overpowered MC"),
    ANTI_HERO("Anti-Hero"),
    VILLAIN_PROTAGONIST("Villain Protagonist"),
    SMART_MC("Smart/Strategic MC"),
    WEAK_TO_STRONG("Weak to Strong"),
    NON_HUMAN_MC("Non-Human MC"),

    // ============ THEMES ============
    REVENGE("Revenge"),
    KINGDOM_BUILDING("Kingdom Building"),
    SURVIVAL("Survival"),
    APOCALYPSE("Apocalypse"),
    ACADEMY("Academy"),
    POLITICS("Politics"),
    
    // ============ RELATIONSHIPS ============
    HAREM("Harem"),
    REVERSE_HAREM("Reverse Harem"),
    BL("Boys Love"),
    GL("Girls Love"),
    
    // ============ MATURE ============
    MATURE("Mature"),
    DARK("Dark/Grim");

    companion object {
        fun fromString(name: String): TagCategory? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}
