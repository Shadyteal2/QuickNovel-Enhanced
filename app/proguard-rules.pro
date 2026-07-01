# =============================================================================
# NeoQN / QuickNovel-Enhanced — ProGuard / R8 rules
# =============================================================================

# Disable class/member renaming (obfuscation) to preserve dynamic plugin compatibility
# and reflection-based JSON serialization (Jackson). Code shrinking remains active.
-dontobfuscate

# ── Stack traces ──────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Jackson (JSON parsing via reflection) ─────────────────────────────────────
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
# Keep all data classes used as Jackson models (annotated or in provider packages)
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty <fields>;
    @com.fasterxml.jackson.annotation.JsonCreator <init>(...);
}
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keep class **.*Response { *; }
-keep class **.*Request { *; }
-keep class **.*Item { *; }
-keep class **.*Data { *; }

# ── Provider dynamic class loading (DexClassLoader) ───────────────────────────
# MainAPI subclasses and companion objects must survive shrinking
-keep class com.lagradost.quicknovel.MainAPI { *; }
-keep class * extends com.lagradost.quicknovel.MainAPI { *; }
-keepclassmembers class * extends com.lagradost.quicknovel.MainAPI { *; }

# ── Plugin / PluginManager infrastructure ─────────────────────────────────────
-keep class com.lagradost.quicknovel.util.PluginItem { *; }
-keep class com.lagradost.quicknovel.util.PluginManager { *; }
-keep class com.lagradost.quicknovel.util.Apis { *; }

# ── API data models (StreamResponse, SearchResponse, etc.) ────────────────────
-keep class com.lagradost.quicknovel.SearchResponse { *; }
-keep class com.lagradost.quicknovel.StreamResponse { *; }
-keep class com.lagradost.quicknovel.EpubResponse { *; }
-keep class com.lagradost.quicknovel.ChapterData { *; }
-keep class com.lagradost.quicknovel.LoadResponse { *; }
-keep class com.lagradost.quicknovel.HeadMainPageResponse { *; }
-keep class com.lagradost.quicknovel.HomePageList { *; }
-keep class com.lagradost.quicknovel.UserReview { *; }

# ── OkHttp / NiceHttp ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keepnames class okhttp3.** { *; }

# ── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil3.**
-keep class coil3.** { *; }

# ── Kotlin coroutines / reflection ───────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**
-keepattributes Signature,*Annotation*,RuntimeVisibleAnnotations,EnclosingMethod,InnerClasses

# ── AndroidX Room ─────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ── Jetpack Compose ───────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── WebView / JS bridge ───────────────────────────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Google ML Kit ──────────────────────────────────────────────────────────────
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }

# ── SLF4J ─────────────────────────────────────────────────────────────────────
-dontwarn org.slf4j.**

# ── Jsoup ─────────────────────────────────────────────────────────────────────
-keep class org.jsoup.** { *; }

# ── Markwon ───────────────────────────────────────────────────────────────────
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# ── EPUBlib ───────────────────────────────────────────────────────────────────
-keep class me.ag2s.epublib.** { *; }
-dontwarn me.ag2s.epublib.**

# ── PDFbox & pdfiumandroid optional dependencies and internal classes ──────────
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn kotlin.coroutines.jvm.internal.SpillingKt

# ── Dynamic Plugin Integration & ClassLoader Protection ───────────────────────
# Keep all application classes to prevent R8 from obfuscating classes loaded or referenced by plugins
-keep class com.lagradost.quicknovel.** { *; }
-keep interface com.lagradost.quicknovel.** { *; }

# Keep all Kotlin standard library classes to prevent ClassCastException (e.g. kotlin.Pair -> q5.i)
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }

# Keep all kotlinx.coroutines classes to protect suspend function states and continuation interfaces
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

# Keep NiceHttp to ensure plugins can make requests stably
-keep class com.lagradost.nicehttp.** { *; }
-keep interface com.lagradost.nicehttp.** { *; }