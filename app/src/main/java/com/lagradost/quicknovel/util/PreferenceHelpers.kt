package com.lagradost.quicknovel.util

import android.content.SharedPreferences

/**
 * Robust preference reading and migration helpers.
 * When migrating settings from one type to another (e.g. Int to Float), 
 * standard SharedPreferences getters will throw ClassCastException.
 * These helpers handle the transition gracefully.
 */

fun SharedPreferences.getSafeInt(key: String, defValue: Int): Int {
    return try {
        this.getInt(key, defValue)
    } catch (e: ClassCastException) {
        try {
            // Try as Float if Int fails
            this.getFloat(key, defValue.toFloat()).toInt()
        } catch (e2: Exception) {
            defValue
        }
    }
}

fun SharedPreferences.getSafeFloat(key: String, defValue: Float): Float {
    return try {
        this.getFloat(key, defValue)
    } catch (e: ClassCastException) {
        try {
            // Try as Int if Float fails
            this.getInt(key, defValue.toInt()).toFloat()
        } catch (e2: Exception) {
            defValue
        }
    }
}

fun SharedPreferences.getSafeBoolean(key: String, defValue: Boolean): Boolean {
    return try {
        this.getBoolean(key, defValue)
    } catch (e: ClassCastException) {
        defValue
    }
}
