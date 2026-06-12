package com.lagradost.quicknovel.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, TTSForegroundService::class.java).apply {
            action = TTSForegroundService.ACTION_PLAY_PAUSE
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

class RewindAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, TTSForegroundService::class.java).apply {
            action = TTSForegroundService.ACTION_PREVIOUS
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

class ForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, TTSForegroundService::class.java).apply {
            action = TTSForegroundService.ACTION_NEXT
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
