package com.lagradost.quicknovel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that PreferenceKeys.kt typed constants have identical string values
 * to the legacy flat constants in DataStore.kt.
 *
 * This ensures: if either side is edited, this test fails, catching silent
 * SharedPreferences key drift before it reaches production (which would lose
 * all user settings for that key).
 *
 * Run with: ./gradlew :app:testDebugUnitTest
 */
class PreferenceKeysConsistencyTest {

    // ── Download ──────────────────────────────────────────────────────────────

    @Test fun `DownloadPrefs FOLDER matches legacy`()                = assertEquals(DOWNLOAD_FOLDER, DownloadPrefs.FOLDER)
    @Test fun `DownloadPrefs SIZE matches legacy`()                  = assertEquals(DOWNLOAD_SIZE, DownloadPrefs.SIZE)
    @Test fun `DownloadPrefs TOTAL matches legacy`()                 = assertEquals(DOWNLOAD_TOTAL, DownloadPrefs.TOTAL)
    @Test fun `DownloadPrefs OFFSET matches legacy`()                = assertEquals(DOWNLOAD_OFFSET, DownloadPrefs.OFFSET)
    @Test fun `DownloadPrefs EPUB_SIZE matches legacy`()             = assertEquals(DOWNLOAD_EPUB_SIZE, DownloadPrefs.EPUB_SIZE)
    @Test fun `DownloadPrefs EPUB_LAST_ACCESS matches legacy`()      = assertEquals(DOWNLOAD_EPUB_LAST_ACCESS, DownloadPrefs.EPUB_LAST_ACCESS)
    @Test fun `DownloadPrefs SORTING_METHOD matches legacy`()        = assertEquals(DOWNLOAD_SORTING_METHOD, DownloadPrefs.SORTING_METHOD)
    @Test fun `DownloadPrefs NORMAL_SORTING_METHOD matches legacy`() = assertEquals(DOWNLOAD_NORMAL_SORTING_METHOD, DownloadPrefs.NORMAL_SORTING_METHOD)
    @Test fun `DownloadPrefs SETTINGS matches legacy`()              = assertEquals(DOWNLOAD_SETTINGS, DownloadPrefs.SETTINGS)

    // ── Reader Display ────────────────────────────────────────────────────────

    @Test fun `ReaderPrefs TEXT_SIZE matches legacy`()               = assertEquals(EPUB_TEXT_SIZE, ReaderPrefs.TEXT_SIZE)
    @Test fun `ReaderPrefs BG_COLOR matches legacy`()                = assertEquals(EPUB_BG_COLOR, ReaderPrefs.BG_COLOR)
    @Test fun `ReaderPrefs TEXT_COLOR matches legacy`()              = assertEquals(EPUB_TEXT_COLOR, ReaderPrefs.TEXT_COLOR)
    @Test fun `ReaderPrefs FONT matches legacy`()                    = assertEquals(EPUB_FONT, ReaderPrefs.FONT)
    @Test fun `ReaderPrefs ZEN_READING matches legacy`()             = assertEquals(EPUB_ZEN_READING, ReaderPrefs.ZEN_READING)
    @Test fun `ReaderPrefs LOCK_ROTATION matches legacy`()           = assertEquals(EPUB_LOCK_ROTATION, ReaderPrefs.LOCK_ROTATION)
    @Test fun `ReaderPrefs HAS_BATTERY matches legacy`()             = assertEquals(EPUB_HAS_BATTERY, ReaderPrefs.HAS_BATTERY)
    @Test fun `ReaderPrefs KEEP_SCREEN_ACTIVE matches legacy`()      = assertEquals(EPUB_KEEP_SCREEN_ACTIVE, ReaderPrefs.KEEP_SCREEN_ACTIVE)
    @Test fun `ReaderPrefs HAS_TIME matches legacy`()                = assertEquals(EPUB_HAS_TIME, ReaderPrefs.HAS_TIME)
    @Test fun `ReaderPrefs TWELVE_HOUR_TIME matches legacy`()        = assertEquals(EPUB_TWELVE_HOUR_TIME, ReaderPrefs.TWELVE_HOUR_TIME)

    // ── Reader Position ───────────────────────────────────────────────────────

    @Test fun `ReaderPrefs CURRENT_POSITION matches legacy`()        = assertEquals(EPUB_CURRENT_POSITION, ReaderPrefs.CURRENT_POSITION)
    @Test fun `ReaderPrefs POSITION_CHAPTER matches legacy`()        = assertEquals(EPUB_CURRENT_POSITION_CHAPTER, ReaderPrefs.POSITION_CHAPTER)

    // ── TTS ───────────────────────────────────────────────────────────────────

    @Test fun `ReaderPrefs Tts SPEED matches legacy`()               = assertEquals(EPUB_TTS_SET_SPEED, ReaderPrefs.Tts.SPEED)
    @Test fun `ReaderPrefs Tts PITCH matches legacy`()               = assertEquals(EPUB_TTS_SET_PITCH, ReaderPrefs.Tts.PITCH)
    @Test fun `ReaderPrefs Tts LOCK matches legacy`()                = assertEquals(EPUB_TTS_LOCK, ReaderPrefs.Tts.LOCK)
    @Test fun `ReaderPrefs Tts SLEEP_TIMER matches legacy`()         = assertEquals(EPUB_SLEEP_TIMER, ReaderPrefs.Tts.SLEEP_TIMER)
    @Test fun `ReaderPrefs Tts LANG matches legacy`()                = assertEquals(EPUB_LANG, ReaderPrefs.Tts.LANG)
    @Test fun `ReaderPrefs Tts VOICE matches legacy`()               = assertEquals(EPUB_VOICE, ReaderPrefs.Tts.VOICE)

    // ── Translation ───────────────────────────────────────────────────────────

    @Test fun `ReaderPrefs Translation FROM_LANGUAGE matches legacy`() = assertEquals(EPUB_ML_FROM_LANGUAGE, ReaderPrefs.Translation.FROM_LANGUAGE)
    @Test fun `ReaderPrefs Translation TO_LANGUAGE matches legacy`()   = assertEquals(EPUB_ML_TO_LANGUAGE, ReaderPrefs.Translation.TO_LANGUAGE)
    @Test fun `ReaderPrefs Translation USE_ONLINE matches legacy`()    = assertEquals(EPUB_ML_USEONLINETRANSLATION, ReaderPrefs.Translation.USE_ONLINE)
    @Test fun `ReaderPrefs Translation CURRENT_ML matches legacy`()    = assertEquals(EPUB_CURRENT_ML, ReaderPrefs.Translation.CURRENT_ML)

    // ── Visual Effects ────────────────────────────────────────────────────────

    @Test fun `ReaderPrefs Effects LUMINESCENT matches legacy`()           = assertEquals(LUMINESCENT_READER, ReaderPrefs.Effects.LUMINESCENT)
    @Test fun `ReaderPrefs Effects LUMINESCENT_INTENSITY matches legacy`() = assertEquals(LUMINESCENT_INTENSITY, ReaderPrefs.Effects.LUMINESCENT_INTENSITY)
    @Test fun `ReaderPrefs Effects LIVING_GLASS matches legacy`()          = assertEquals(LIVING_GLASS, ReaderPrefs.Effects.LIVING_GLASS)
    @Test fun `ReaderPrefs Effects AURA_INTENSITY matches legacy`()        = assertEquals(AURA_INTENSITY, ReaderPrefs.Effects.AURA_INTENSITY)
    @Test fun `ReaderPrefs Effects AURA_SPEED matches legacy`()            = assertEquals(AURA_SPEED, ReaderPrefs.Effects.AURA_SPEED)
    @Test fun `ReaderPrefs Effects AURA_PALETTE matches legacy`()          = assertEquals(AURA_PALETTE, ReaderPrefs.Effects.AURA_PALETTE)

    // ── Result / Chapter ──────────────────────────────────────────────────────

    @Test fun `ResultPrefs CHAPTER_SORT matches legacy`()            = assertEquals(RESULT_CHAPTER_SORT, ResultPrefs.CHAPTER_SORT)
    @Test fun `ResultPrefs FILTER_DOWNLOADED matches legacy`()       = assertEquals(RESULT_CHAPTER_FILTER_DOWNLOADED, ResultPrefs.FILTER_DOWNLOADED)
    @Test fun `ResultPrefs BOOKMARK matches legacy`()                = assertEquals(RESULT_BOOKMARK, ResultPrefs.BOOKMARK)
    @Test fun `ResultPrefs BOOKMARK_STATE matches legacy`()          = assertEquals(RESULT_BOOKMARK_STATE, ResultPrefs.BOOKMARK_STATE)
    @Test fun `ResultPrefs CHAPTER_BOOKMARK matches legacy`()        = assertEquals(RESULT_CHAPTER_BOOKMARK, ResultPrefs.CHAPTER_BOOKMARK)

    // ── App ───────────────────────────────────────────────────────────────────

    @Test fun `AppPrefs CURRENT_TAB matches legacy`()                = assertEquals(CURRENT_TAB, AppPrefs.CURRENT_TAB)
    @Test fun `AppPrefs HISTORY_FOLDER matches legacy`()             = assertEquals(HISTORY_FOLDER, AppPrefs.HISTORY_FOLDER)
    @Test fun `AppPrefs PREFERENCES_NAME matches legacy`()           = assertEquals(PREFERENCES_NAME, AppPrefs.PREFERENCES_NAME)
}
