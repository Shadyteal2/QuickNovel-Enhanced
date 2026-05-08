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

    private const val DAILY_READING_TIME_PREFIX = "DAILY_READING_TIME_"

    fun incrementChapterRead(context: Context) {
        val current = context.getKey<Int>(TOTAL_CHAPTERS_READ, 0) ?: 0
        context.setKey(TOTAL_CHAPTERS_READ, current + 1)
        updateStreak(context)
    }

    fun addReadingTime(context: Context, timeMs: Long) {
        if (timeMs <= 0) return
        
        // 1. Update Total
        val total = context.getKey<Long>(TOTAL_READING_TIME, 0L) ?: 0L
        context.setKey(TOTAL_READING_TIME, total + timeMs)

        // 2. Update Daily Total
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val dailyKey = DAILY_READING_TIME_PREFIX + today
        val currentDaily = context.getKey<Long>(dailyKey, 0L) ?: 0L
        context.setKey(dailyKey, currentDaily + timeMs)

        updateStreak(context)
    }

    fun getDailyTimeMs(context: Context, date: java.util.Date): Long {
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(date)
        return context.getKey<Long>(DAILY_READING_TIME_PREFIX + dateStr, 0L) ?: 0L
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
        if (lastRead != 0L && 
            lastReadCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            lastReadCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
        ) {
            context.setKey(LAST_READ_TIMESTAMP, now)
            return
        }

        // Check if yesterday
        val yesterdayCal = Calendar.getInstance().apply { 
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        
        val wasYesterday = lastRead != 0L &&
                lastReadCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                lastReadCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

        if (wasYesterday) {
            val currentStreak = context.getKey<Int>(CURRENT_STREAK, 0) ?: 0
            val newStreak = currentStreak + 1
            context.setKey(CURRENT_STREAK, newStreak)
            
            val bestStreak = context.getKey<Int>(BEST_STREAK, 0) ?: 0
            if (newStreak > bestStreak) {
                context.setKey(BEST_STREAK, newStreak)
            }
        } else {
            // Gap in reading or first time, reset streak to 1
            context.setKey(CURRENT_STREAK, 1)
        }

        context.setKey(LAST_READ_TIMESTAMP, now)
    }
}
