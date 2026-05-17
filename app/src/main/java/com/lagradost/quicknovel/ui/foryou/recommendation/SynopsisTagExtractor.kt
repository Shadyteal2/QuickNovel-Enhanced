package com.lagradost.quicknovel.ui.foryou.recommendation

import java.util.Locale

object SynopsisTagExtractor {

    private val titlePatterns = listOf(
        listOf("cultivation", "cultivator", "immortal", "dao", "sect", "martial") to TagCategory.CULTIVATION,
        listOf("wuxia") to TagCategory.WUXIA,
        listOf("xianxia") to TagCategory.XIANXIA,
        listOf("murim") to TagCategory.MURIM,
        listOf("isekai", "another world", "otherworld", "transported") to TagCategory.ISEKAI,
        listOf("reincarnated", "reincarnation", "rebirth", "reborn") to TagCategory.REINCARNATION,
        listOf("transmigrat") to TagCategory.TRANSMIGRATION,
        listOf("regressor", "regression", "return", "second chance") to TagCategory.REGRESSION,
        listOf("time loop", "loop") to TagCategory.TIME_LOOP,
        listOf("litrpg", "lit rpg") to TagCategory.LITRPG,
        listOf("system", "status window") to TagCategory.SYSTEM,
        listOf("dungeon") to TagCategory.DUNGEON,
        listOf("tower") to TagCategory.TOWER,
        listOf("level", "leveling") to TagCategory.PROGRESSION,
        listOf("harem") to TagCategory.HAREM,
        listOf("romance", "love") to TagCategory.ROMANCE,
        listOf("bl", "boys love", "yaoi", "danmei") to TagCategory.BL,
        listOf("gl", "girls love", "yuri", "baihe") to TagCategory.GL,
        listOf("villainess", "villain") to TagCategory.VILLAIN_PROTAGONIST,
        listOf("op mc", "overpowered", "strongest", "invincible") to TagCategory.OP_MC,
        listOf("weak to strong", "zero to hero") to TagCategory.WEAK_TO_STRONG,
        listOf("dragon") to TagCategory.FANTASY,
        listOf("revenge", "vengeance") to TagCategory.REVENGE,
        listOf("kingdom building", "empire") to TagCategory.KINGDOM_BUILDING,
        listOf("apocalypse", "post-apocalyptic") to TagCategory.APOCALYPSE,
        listOf("survival", "survive") to TagCategory.SURVIVAL,
        listOf("academy", "school") to TagCategory.ACADEMY,
        listOf("fantasy") to TagCategory.FANTASY,
        listOf("sci-fi", "scifi", "science fiction") to TagCategory.SCI_FI,
        listOf("horror") to TagCategory.HORROR,
        listOf("mystery") to TagCategory.MYSTERY,
        listOf("thriller") to TagCategory.THRILLER,
        listOf("comedy", "humor") to TagCategory.COMEDY
    )

    private val extractionPatterns = listOf(
        listOf("cultivation", "qi ", "dantian", "golden core", "sect", "elder", "breakthrough") to TagCategory.CULTIVATION,
        listOf("wuxia", "jianghu") to TagCategory.WUXIA,
        listOf("xianxia", "daoist", "heavenly dao") to TagCategory.XIANXIA,
        listOf("murim", "martial world") to TagCategory.MURIM,
        listOf("martial arts", "martial artist", "kung fu") to TagCategory.MARTIAL_ARTS,
        listOf("isekai", "transported to", "summoned to", "another world") to TagCategory.ISEKAI,
        listOf("reincarnated", "reincarnation", "rebirth", "past life") to TagCategory.REINCARNATION,
        listOf("transmigrated", "transmigration", "possessed") to TagCategory.TRANSMIGRATION,
        listOf("regressor", "regression", "returned to the past") to TagCategory.REGRESSION,
        listOf("time loop", "stuck in a loop") to TagCategory.TIME_LOOP,
        listOf("level up", "experience points", "skill tree", "litrpg") to TagCategory.LITRPG,
        listOf("[system", "status window", "notification", "quest received") to TagCategory.SYSTEM,
        listOf("dungeon", "floor boss", "monster room") to TagCategory.DUNGEON,
        listOf("tower", "climbing the tower", "floor ") to TagCategory.TOWER,
        listOf("grow stronger", "become stronger", "progression") to TagCategory.PROGRESSION,
        listOf("harem", "multiple wives", "beauties") to TagCategory.HAREM,
        listOf("reverse harem", "many suitors") to TagCategory.REVERSE_HAREM,
        listOf("boys love", "bl", "yaoi", "danmei") to TagCategory.BL,
        listOf("girls love", "gl", "yuri", "baihe") to TagCategory.GL,
        listOf("overpowered", "op mc", "strongest", "unbeatable") to TagCategory.OP_MC,
        listOf("weak to strong", "started weak", "trash of the family") to TagCategory.WEAK_TO_STRONG,
        listOf("anti-hero", "antihero", "not a hero") to TagCategory.ANTI_HERO,
        listOf("villain", "villain protagonist", "dark lord") to TagCategory.VILLAIN_PROTAGONIST,
        listOf("genius", "prodigy", "smart mc") to TagCategory.SMART_MC,
        listOf("revenge", "vengeance", "avenge") to TagCategory.REVENGE,
        listOf("kingdom building", "nation building", "territory management") to TagCategory.KINGDOM_BUILDING,
        listOf("survival", "survive", "fight to survive") to TagCategory.SURVIVAL,
        listOf("apocalypse", "end of the world", "zombie") to TagCategory.APOCALYPSE,
        listOf("academy", "magic academy", "enrolled in") to TagCategory.ACADEMY,
        listOf("politics", "court intrigue", "noble houses") to TagCategory.POLITICS,
        listOf("dark", "grim", "bleak", "hopeless") to TagCategory.DARK,
        listOf("non-human", "monster protagonist", "slime", "skeleton") to TagCategory.NON_HUMAN_MC
    )

    fun extractFromTitle(title: String?): Set<TagCategory> {
        if (title.isNullOrBlank()) return emptySet()
        val lower = title.lowercase(Locale.ROOT)
        val tags = mutableSetOf<TagCategory>()
        for ((keywords, tag) in titlePatterns) {
            if (keywords.any { it in lower }) {
                tags.add(tag)
            }
        }
        return tags
    }

    fun extractTags(synopsis: String?): Set<TagCategory> {
        if (synopsis.isNullOrBlank()) return emptySet()
        val lower = synopsis.lowercase(Locale.ROOT)
        val tagScores = mutableMapOf<TagCategory, Int>()
        for ((keywords, tag) in extractionPatterns) {
            val matches = keywords.count { it in lower }
            if (matches > 0) {
                tagScores[tag] = (tagScores[tag] ?: 0) + matches
            }
        }
        return tagScores.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
            .toSet()
    }

    fun extractAll(title: String?, synopsis: String?): Set<TagCategory> {
        return extractFromTitle(title) + extractTags(synopsis)
    }
}
