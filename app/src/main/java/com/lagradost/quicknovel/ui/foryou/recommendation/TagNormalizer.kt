package com.lagradost.quicknovel.ui.foryou.recommendation

import java.util.Locale

object TagNormalizer {

    private val tagAliases: Map<String, TagCategory> = buildMap {
        put("action", TagCategory.ACTION)
        put("adventure", TagCategory.ADVENTURE)
        put("comedy", TagCategory.COMEDY)
        put("humor", TagCategory.COMEDY)
        put("drama", TagCategory.DRAMA)
        put("fantasy", TagCategory.FANTASY)
        put("horror", TagCategory.HORROR)
        put("mystery", TagCategory.MYSTERY)
        put("romance", TagCategory.ROMANCE)
        put("love", TagCategory.ROMANCE)
        put("sci-fi", TagCategory.SCI_FI)
        put("scifi", TagCategory.SCI_FI)
        put("science fiction", TagCategory.SCI_FI)
        put("slice of life", TagCategory.SLICE_OF_LIFE)
        put("sol", TagCategory.SLICE_OF_LIFE)
        put("thriller", TagCategory.THRILLER)
        put("tragedy", TagCategory.TRAGEDY)

        // Eastern
        put("cultivation", TagCategory.CULTIVATION)
        put("wuxia", TagCategory.WUXIA)
        put("xianxia", TagCategory.XIANXIA)
        put("xuanhuan", TagCategory.XUANHUAN)
        put("murim", TagCategory.MURIM)
        put("martial arts", TagCategory.MARTIAL_ARTS)

        // Isekai
        put("isekai", TagCategory.ISEKAI)
        put("reincarnation", TagCategory.REINCARNATION)
        put("reincarnated", TagCategory.REINCARNATION)
        put("rebirth", TagCategory.REINCARNATION)
        put("transmigration", TagCategory.TRANSMIGRATION)
        put("regression", TagCategory.REGRESSION)
        put("time loop", TagCategory.TIME_LOOP)

        // Game/LitRPG
        put("litrpg", TagCategory.LITRPG)
        put("lit rpg", TagCategory.LITRPG)
        put("gamelit", TagCategory.GAMELIT)
        put("system", TagCategory.SYSTEM)
        put("game system", TagCategory.SYSTEM)
        put("progression", TagCategory.PROGRESSION)
        put("dungeon", TagCategory.DUNGEON)
        put("tower", TagCategory.TOWER)

        // Lead Types
        put("op mc", TagCategory.OP_MC)
        put("overpowered", TagCategory.OP_MC)
        put("anti-hero", TagCategory.ANTI_HERO)
        put("antihero", TagCategory.ANTI_HERO)
        put("villain protagonist", TagCategory.VILLAIN_PROTAGONIST)
        put("villain mc", TagCategory.VILLAIN_PROTAGONIST)
        put("smart mc", TagCategory.SMART_MC)
        put("weak to strong", TagCategory.WEAK_TO_STRONG)
        put("non-human mc", TagCategory.NON_HUMAN_MC)

        // Themes
        put("revenge", TagCategory.REVENGE)
        put("kingdom building", TagCategory.KINGDOM_BUILDING)
        put("survival", TagCategory.SURVIVAL)
        put("apocalypse", TagCategory.APOCALYPSE)
        put("academy", TagCategory.ACADEMY)
        put("politics", TagCategory.POLITICS)

        // Relationships
        put("harem", TagCategory.HAREM)
        put("reverse harem", TagCategory.REVERSE_HAREM)
        put("bl", TagCategory.BL)
        put("boys love", TagCategory.BL)
        put("gl", TagCategory.GL)
        put("girls love", TagCategory.GL)

        // Mature
        put("mature", TagCategory.MATURE)
        put("adult", TagCategory.MATURE)
        put("dark", TagCategory.DARK)
        put("grimdark", TagCategory.DARK)
    }

    /**
     * Maps a raw list of provider tags to a set of canonical TagCategories.
     */
    fun normalize(tags: List<String>?): Set<TagCategory> {
        if (tags == null) return emptySet()
        val normalized = mutableSetOf<TagCategory>()
        for (tag in tags) {
            val lower = tag.lowercase(Locale.ROOT).trim()
            tagAliases[lower]?.let { normalized.add(it) }
            
            // Handle some compound tags or partial matches if needed
            if (lower.contains("cultivation")) normalized.add(TagCategory.CULTIVATION)
            if (lower.contains("system")) normalized.add(TagCategory.SYSTEM)
            if (lower.contains("isekai")) normalized.add(TagCategory.ISEKAI)
        }
        return normalized
    }

    fun getDisplayName(category: TagCategory): String {
        return category.displayName
    }
}
