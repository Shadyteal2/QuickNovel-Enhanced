package com.lagradost.quicknovel.ui.custom

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.SingleSelectionHelper.showBottomDialog

/**
 * Novel detail layout picker (Classic / Modern).
 * Handles its own click + dialog so it works with custom [setting_tile] layouts.
 */
class DetailScreenStylePreference(
    context: Context,
    attrs: AttributeSet?
) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.setting_tile
        isSelectable = true
        updateSummary()
    }

    private fun prefKey(): String = context.getString(R.string.detail_screen_style_key)

    private fun updateSummary() {
        val current = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(prefKey(), "0") ?: "0"
        summary = when (current) {
            "1" -> context.getString(R.string.detail_screen_style_modern)
            else -> context.getString(R.string.detail_screen_style_classic)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // Custom setting_tile layout: ensure taps reach onClick()
        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener { onClick() }
    }

    override fun onClick() {
        val host = (context as? Activity) ?: CommonActivity.activity ?: return
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val names = listOf(
            context.getString(R.string.detail_screen_style_classic),
            context.getString(R.string.detail_screen_style_modern),
        )
        val values = listOf("0", "1")
        val current = settings.getString(prefKey(), "0") ?: "0"
        val index = values.indexOf(current).coerceAtLeast(0)

        host.showBottomDialog(
            names,
            index,
            context.getString(R.string.detail_screen_style),
            false,
            {},
        ) { selected ->
            settings.edit { putString(prefKey(), values[selected]) }
            updateSummary()
        }
    }
}
