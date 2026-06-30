package com.lagradost.quicknovel.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ContinueReadingWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetCarouselRotationManager.start(context)
        scope.launch {
            ContinueReadingWidget.refreshFromHistory(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetCarouselRotationManager.start(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        WidgetCarouselRotationManager.start(context)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            scope.launch {
                ContinueReadingWidget.refreshFromHistory(context)
            }
        }
    }
}
