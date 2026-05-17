package com.lagradost.quicknovel

/**
 * Typed, organized preference key constants.
 *
 * This file organizes all SharedPreference keys into domain-grouped objects.
 * The underlying string values are identical to the legacy flat constants in DataStore.kt,
 * so new and old code interoperate on the same SharedPreferences file.
 *
 * ## Migration guide
 * Old: `getKey(EPUB_TEXT_SIZE, 14)`
 * New: `getKey(ReaderPrefs.TEXT_SIZE, 14)`
 *
 * Legacy constants in DataStore.kt remain fully valid — migrate them over time.
 */

// ─── Download ────────────────────────────────────────────────────────────────

/** All keys related to the chapter download system. */
object DownloadPrefs {
    const val FOLDER                = "downloads_data"
    const val SIZE                  = "downloads_size"
    const val TOTAL                 = "downloads_total"
    const val OFFSET                = "downloads_offset"
    const val EPUB_SIZE             = "downloads_epub_size"
    const val EPUB_LAST_ACCESS      = "downloads_epub_last_access"
    const val SORTING_METHOD        = "download_sorting"
    const val NORMAL_SORTING_METHOD = "download_normal_sorting"
    const val SETTINGS              = "download_settings"
}

// ─── Reader ──────────────────────────────────────────────────────────────────

/** All keys related to the epub/reader screen, grouped by sub-feature. */
object ReaderPrefs {

    // ── Display & Layout ─────────────────────────────────────────────────────
    const val LOCK_ROTATION         = "reader_epub_rotation"
    const val TEXT_SIZE             = "reader_epub_text_size"
    const val TEXT_BIONIC           = "reader_epub_bionic_reading"
    const val TEXT_SELECTABLE       = "reader_epub_text_selectable"
    const val DICTIONARY_ENABLED    = "reader_epub_dictionary_enabled"
    const val SCROLL_VOL            = "reader_epub_scroll_volume"
    const val AUTHOR_NOTES          = "reader_epub_author_notes"
    const val BG_COLOR              = "reader_epub_bg_color"
    const val TEXT_COLOR            = "reader_epub_text_color"
    const val TEXT_VERTICAL_PADDING = "reader_epub_vertical_padding"
    const val TEXT_PADDING          = "reader_epub_text_padding"
    const val TEXT_PADDING_TOP      = "reader_epub_text_padding_top"
    const val FONT                  = "reader_epub_font"
    const val READER_TYPE           = "reader_reader_type"
    const val SHOW_READER_PROGRESS  = "reader_epub_show_progress"

    // ── Status bar / overlay ──────────────────────────────────────────────────
    const val HAS_BATTERY           = "reader_epub_has_battery"
    const val KEEP_SCREEN_ACTIVE    = "reader_epub_keep_screen_active"
    const val HAS_TIME              = "reader_epub_has_time"
    const val TWELVE_HOUR_TIME      = "reader_epub_twelve_hour_time"
    const val ZEN_READING           = "reader_epub_zen_reading"

    // ── Chapter position tracking ─────────────────────────────────────────────
    const val CURRENT_POSITION      = "reader_epub_position"
    const val POSITION_SCROLL       = "reader_epub_position_scroll"
    const val POSITION_SCROLL_CHAR  = "reader_epub_position_scroll_char"
    const val POSITION_READ_AT      = "reader_epub_position_read"
    const val POSITION_CHAPTER      = "reader_epub_position_chapter"

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    object Tts {
        const val LOCK              = "reader_epub_scroll_lock"
        const val SPEED             = "reader_epub_tts_speed"
        const val PITCH             = "reader_epub_tts_pitch"
        const val SLEEP_TIMER       = "reader_epub_tts_timer"
        const val LANG              = "reader_epub_lang"
        const val VOICE             = "reader_epub_voice"
    }

    // ── Machine Translation ───────────────────────────────────────────────────
    object Translation {
        const val FROM_LANGUAGE     = "reader_epub_ml_from"
        const val TO_LANGUAGE       = "reader_epub_ml_to"
        const val USE_ONLINE        = "reader_epub_ml_use_online"
        const val CURRENT_ML        = "reader_epub_ml"
    }

    // ── Visual Effects (Aura / Luminescent) ───────────────────────────────────
    /** Keys for the premium visual effects engine (LivingGlass / Aura / Luminescent reader). */
    object Effects {
        const val LUMINESCENT           = "luminescent_reader"
        const val LUMINESCENT_INTENSITY = "luminescent_intensity"
        const val LIVING_GLASS          = "living_glass_key"
        const val AURA_INTENSITY        = "aura_intensity_key"
        const val AURA_SPEED            = "aura_speed_key"
        const val AURA_PALETTE          = "aura_palette_key"
        const val PREMIUM_ANIMATIONS    = "premium_animations_key"
    }
}

// ─── Result / Chapter list ───────────────────────────────────────────────────

/** Keys for the result/detail screen — chapter filters, bookmarks. */
object ResultPrefs {
    const val CHAPTER_SORT          = "result_chapter_sort"
    const val FILTER_DOWNLOADED     = "result_chapter_filter_download"
    const val FILTER_BOOKMARKED     = "result_chapter_filter_bookmarked"
    const val FILTER_READ           = "result_chapter_filter_read"
    const val FILTER_UNREAD         = "result_chapter_filter_unread"
    const val BOOKMARK              = "result_bookmarked"
    const val BOOKMARK_STATE        = "result_bookmarked_state"
    const val CHAPTER_BOOKMARK      = "result_chapter_bookmarked"
}

// ─── App-wide ────────────────────────────────────────────────────────────────

/** App-wide keys not tied to a specific feature. */
object AppPrefs {
    const val PREFERENCES_NAME      = "rebuild_preference"
    const val NOVEL_REPLACEMENTS    = "novel_replacements"
    const val HISTORY_FOLDER        = "result_history"
    const val CURRENT_TAB           = "current_tab"
}
