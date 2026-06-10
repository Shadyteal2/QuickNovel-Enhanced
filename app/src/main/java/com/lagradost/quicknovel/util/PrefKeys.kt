package com.lagradost.quicknovel.util

object PrefKeys {
    // Core Translation Settings
    const val TRANSLATION_ENGINE_ID = "translation_engine_key"
    const val TRANSLATION_FROM_LANG = "translation_source_lang_key"
    const val TRANSLATION_TO_LANG = "translation_target_lang_key"
    const val USE_ONLINE_TRANSLATION = "pref_use_online_translation"
    const val TRANSLATION_AUTO = "pref_translation_auto"
    
    // Engine Specific Models (Removed legacy AI engines)
    
    // API Keys (Removed legacy AI engines)
    const val TRANSLATION_API_URL = "pref_translation_api_url"
    const val TRANSLATION_API_KEY = "pref_translation_api_key"
    const val TRANSLATION_API_MODEL = "pref_translation_api_model"

    // Cloud AI rate-limit & throughput tuning (user-configurable)
    const val CLOUD_AI_MAX_PARALLEL = "pref_cloud_ai_max_parallel"    // Default: 1
    const val CLOUD_AI_DELAY_MS     = "pref_cloud_ai_delay_ms"        // Default: 2000
    const val CLOUD_AI_BATCH_SIZE   = "pref_cloud_ai_batch_size"      // Default: 5
    const val CLOUD_AI_PROVIDER     = "pref_cloud_ai_provider"        // Default: "gemini"

    
    // Customization
    const val TRANSLATION_CONTENT_TYPE = "pref_translation_content_type"
    const val TRANSLATION_TONE = "pref_translation_tone"
    const val TRANSLATION_SESSION_ENABLED = "epub_session_translation_enabled"
}

