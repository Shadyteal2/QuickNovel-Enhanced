package com.lagradost.quicknovel.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.cornerRadius
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.asDrawable
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.ReadActivity2
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.TTSNotificationService
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object TTSWidgetKeys {
    val NOVEL_TITLE = stringPreferencesKey("novel_title")
    val CHAPTER_NAME = stringPreferencesKey("chapter_name")
    val COVER_PATH = stringPreferencesKey("cover_path")
    val IS_PLAYING = booleanPreferencesKey("is_playing")
    val NOVEL_URL = stringPreferencesKey("novel_url")
    val CHAPTER_INDEX = intPreferencesKey("chapter_index")
}

class TTSWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = androidx.glance.currentState<Preferences>()
            val title = prefs[TTSWidgetKeys.NOVEL_TITLE] ?: context.getString(R.string.no_data)
            val chapter = prefs[TTSWidgetKeys.CHAPTER_NAME] ?: context.getString(R.string.loading)
            val isPlaying = prefs[TTSWidgetKeys.IS_PLAYING] ?: false
            val coverPath = prefs[TTSWidgetKeys.COVER_PATH] ?: ""
            val novelUrl = prefs[TTSWidgetKeys.NOVEL_URL] ?: ""
            val chapterIndex = prefs[TTSWidgetKeys.CHAPTER_INDEX] ?: -1

            // Load local cached cover art bitmap if path is present and valid
            val coverBitmap: Bitmap? = if (coverPath.isNotEmpty()) {
                try {
                    BitmapFactory.decodeFile(coverPath)
                } catch (t: Throwable) {
                    null
                }
            } else {
                null
            }

            val defaultTextColor = ColorProvider(
                day = androidx.compose.ui.graphics.Color.Black,
                night = androidx.compose.ui.graphics.Color.White
            )
            val mutedTextColor = ColorProvider(
                day = androidx.compose.ui.graphics.Color(0xFF555555),
                night = androidx.compose.ui.graphics.Color(0xFFCCCCCC)
            )

            val launchIntent = Intent(context, ReadActivity2::class.java).apply {
                putExtra("novelUrl", novelUrl)
                putExtra("novelTitle", title)
                putExtra("chapterIndex", chapterIndex)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            // Outer transparent container
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(actionStartActivity(launchIntent)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover Art image (Left)
                    Box(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .width(85.dp)
                            .cornerRadius(12.dp)
                    ) {
                        Image(
                            provider = if (coverBitmap != null) {
                                ImageProvider(coverBitmap)
                            } else {
                                ImageProvider(R.drawable.ic_baseline_menu_book_24)
                            },
                            contentDescription = "Cover Art",
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }

                    // Solid-color control card Column (Right) overlapping visual layout style
                    Column(
                        modifier = GlanceModifier
                            .fillMaxHeight()
                            .defaultWeight()
                            .background(ImageProvider(R.drawable.widget_control_card))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = TextStyle(
                                color = defaultTextColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = chapter,
                            style = TextStyle(
                                color = mutedTextColor,
                                fontSize = 13.sp
                            ),
                            maxLines = 1
                        )

                        Spacer(modifier = GlanceModifier.height(12.dp))

                        // Media player action controls row
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous / Rewind Button
                            Image(
                                provider = ImageProvider(R.drawable.ic_baseline_fast_rewind_24),
                                contentDescription = "Previous",
                                colorFilter = ColorFilter.tint(defaultTextColor),
                                modifier = GlanceModifier
                                    .size(32.dp)
                                    .clickable(actionRunCallback<RewindAction>())
                            )

                            Spacer(modifier = GlanceModifier.width(16.dp))

                            // Play / Pause Button
                            Image(
                                provider = if (isPlaying) {
                                    ImageProvider(R.drawable.ic_baseline_pause_24)
                                } else {
                                    ImageProvider(R.drawable.ic_baseline_play_arrow_24)
                                },
                                contentDescription = "Play Pause",
                                colorFilter = ColorFilter.tint(defaultTextColor),
                                modifier = GlanceModifier
                                    .size(40.dp)
                                    .clickable(actionRunCallback<PlayPauseAction>())
                            )

                            Spacer(modifier = GlanceModifier.width(16.dp))

                            // Next / Forward Button
                            Image(
                                provider = ImageProvider(R.drawable.ic_baseline_fast_forward_24),
                                contentDescription = "Next",
                                colorFilter = ColorFilter.tint(defaultTextColor),
                                modifier = GlanceModifier
                                    .size(32.dp)
                                    .clickable(actionRunCallback<ForwardAction>())
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private suspend fun fetchCoverBitmap(context: Context, url: String?): Bitmap? {
            if (url.isNullOrBlank()) return null
            return withContext(Dispatchers.IO) {
                try {
                    val loader = SingletonImageLoader.get(context)
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false) // Must be non-hardware bitmap for RemoteViews/Notifications usage
                        .build()
                    val result = loader.execute(request)
                    val drawable = (result as? SuccessResult)?.image?.asDrawable(context.resources)
                    (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } catch (t: Throwable) {
                    null
                }
            }
        }

        suspend fun updateWidgetState(
            context: Context,
            novelTitle: String,
            chapterName: String,
            coverUrl: String?,
            isPlaying: Boolean,
            coverBitmap: Bitmap? = null,
            novelUrl: String? = null,
            chapterIndex: Int? = null
        ) {
            val bitmap = coverBitmap ?: fetchCoverBitmap(context, coverUrl)
            val cacheFile = File(context.cacheDir, "tts_widget_cover.png")
            if (bitmap != null) {
                safeApiCall {
                    FileOutputStream(cacheFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else {
                if (cacheFile.exists()) cacheFile.delete()
            }

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TTSWidget::class.java)
            for (id in glanceIds) {
                updateAppWidgetState(context, id) { prefs ->
                    prefs[TTSWidgetKeys.NOVEL_TITLE] = novelTitle
                    prefs[TTSWidgetKeys.CHAPTER_NAME] = chapterName
                    prefs[TTSWidgetKeys.IS_PLAYING] = isPlaying
                    prefs[TTSWidgetKeys.COVER_PATH] = if (cacheFile.exists()) cacheFile.absolutePath else ""
                    if (novelUrl != null) prefs[TTSWidgetKeys.NOVEL_URL] = novelUrl
                    if (chapterIndex != null) prefs[TTSWidgetKeys.CHAPTER_INDEX] = chapterIndex
                }
                TTSWidget().update(context, id)
            }
        }

        suspend fun updateAll(context: Context) {
            withContext(Dispatchers.IO) {
                val keys = context.getKeys(HISTORY_FOLDER) ?: return@withContext
                val lastNovel = keys.mapNotNull { key ->
                    context.getKey<ResultCached>(key)
                }.maxByOrNull { it.cachedTime } ?: return@withContext

                val chapterIndex = context.getKey<Int>(EPUB_CURRENT_POSITION, lastNovel.name) ?: 0
                val chapterName = context.getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, lastNovel.name)
                    ?: "Chapter ${chapterIndex + 1}"
                
                // Determine active playing status
                val isPlaying = TTSNotificationService.viewModel?.isTTSRunning() == true

                updateWidgetState(
                    context = context,
                    novelTitle = lastNovel.name,
                    chapterName = chapterName,
                    coverUrl = lastNovel.poster,
                    isPlaying = isPlaying,
                    coverBitmap = null,
                    novelUrl = lastNovel.source,
                    chapterIndex = chapterIndex
                )
            }
        }
    }
}
