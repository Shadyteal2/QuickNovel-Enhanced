package com.lagradost.quicknovel.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lagradost.quicknovel.R

/**
 * A custom preference for the Hero card that displays app info and version.
 */
class HeroPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.setting_hero_card
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val pc = context.packageManager.getPackageInfo(context.packageName, 0)
        holder.findViewById(R.id.app_version)?.let {
            (it as TextView).text = "v${pc.versionName}"
        }
    }
}

/**
 * A custom preference for the Bento Grid that handles navigation to other sections.
 */
class BentoPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    var onBentoClick: ((String) -> Unit)? = null

    init {
        layoutResource = R.layout.setting_bento_grid
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        setupBentoItem(holder, R.id.bento_appearance, "appearance")
        setupBentoItem(holder, R.id.bento_reader, "general") // Mapping to general for now
        setupBentoItem(holder, R.id.bento_storage, "search")
        setupBentoItem(holder, R.id.bento_advanced, "dev_only")
    }

    private fun setupBentoItem(holder: PreferenceViewHolder, viewId: Int, categoryKey: String) {
        holder.findViewById(viewId)?.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) // Lighter haptic for quick clicks
            onBentoClick?.invoke(categoryKey)
        }
    }
}

/**
 * A custom preference for Social Chips.
 */
class SocialChipsPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.setting_social_chips
    }
    
    // Safe URL opening to prevent crashes if no browser/app is installed
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        setupSocialChip(holder, R.id.chip_discord, "https://discord.gg/njMumTKvVw")
        setupSocialChip(holder, R.id.chip_github, "https://github.com/Shadyteal2/QuickNovel-Enhanced")
        setupSocialChip(holder, R.id.chip_telegram, "https://t.me/+i9MSwgeoXzU0NTE1")
    }

    private fun setupSocialChip(holder: PreferenceViewHolder, viewId: Int, url: String) {
        holder.findViewById(viewId)?.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                // Silently fail or show a toast if no activity can handle it
                android.widget.Toast.makeText(context, "No app found to open this link", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
