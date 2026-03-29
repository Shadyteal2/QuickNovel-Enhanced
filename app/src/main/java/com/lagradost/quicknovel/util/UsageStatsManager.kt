package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import java.util.Calendar

object UsageStatsManager {
    // Keys used in DataStore
    private const val TOTAL_READING_TIME = "TOTAL_READING_TIME"
    private const val TOTAL_CHAPTERS_READ = "TOTAL_CHAPTERS_READ"
    private const val CURRENT_STREAK = "CURRENT_STREAK"
    private const val BEST_STREAK = "BEST_STREAK"
    private const val LAST_READ_TIMESTAMP = "LAST_READ_TIMESTAMP"
    private const val CUSTOMIZATION_COUNT = "CUSTOMIZATION_COUNT"

    fun incrementChapterRead(context: Context) {
        val current = context.getKey<Int>(TOTAL_CHAPTERS_READ, 0) ?: 0
        context.setKey(TOTAL_CHAPTERS_READ, current + 1)
        updateStreak(context)
    }

    fun addReadingTime(context: Context, timeMs: Long) {
        if (timeMs <= 0) return
        val current = context.getKey<Long>(TOTAL_READING_TIME, 0L) ?: 0L
        context.setKey(TOTAL_READING_TIME, current + timeMs)
        updateStreak(context)
    }

    fun incrementCustomization(context: Context) {
        val current = context.getKey<Int>(CUSTOMIZATION_COUNT, 0) ?: 0
        context.setKey(CUSTOMIZATION_COUNT, current + 1)
    }

    private fun updateStreak(context: Context) {
        val lastRead = context.getKey<Long>(LAST_READ_TIMESTAMP, 0L) ?: 0L
        val now = System.currentTimeMillis()
        
        val lastReadCal = Calendar.getInstance().apply { timeInMillis = lastRead }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }

        // Same day, nothing to update for streak count, just timestamp
        if (lastReadCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            lastReadCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
        ) {
            context.setKey(LAST_READ_TIMESTAMP, now)
            return
        }

        // Check if yesterday
        lastReadCal.add(Calendar.DAY_OF_YEAR, 1)
        val wasYesterday = lastReadCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                lastReadCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (wasYesterday) {
            val currentStreak = context.getKey<Int>(CURRENT_STREAK, 0) ?: 0
            val newStreak = currentStreak + 1
            context.setKey(CURRENT_STREAK, newStreak)
            
            val bestStreak = context.getKey<Int>(BEST_STREAK, 0) ?: 0
            if (newStreak > bestStreak) {
                context.setKey(BEST_STREAK, newStreak)
            }
        } else if (lastRead != 0L) {
            // Gap in reading, reset streak
            context.setKey(CURRENT_STREAK, 1)
        } else {
            // First time reading
            context.setKey(CURRENT_STREAK, 1)
        }

        context.setKey(LAST_READ_TIMESTAMP, now)
    }
}
