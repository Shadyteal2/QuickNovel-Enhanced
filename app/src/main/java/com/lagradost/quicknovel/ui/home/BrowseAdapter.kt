package com.lagradost.quicknovel.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.BrowseListCompactBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment

class BrowseAdapter : NoStateAdapter<MainAPI>(BaseDiffCallback(itemSame = { a, b ->
    a.name == b.name
}, contentSame = { a, b ->
    a.name == b.name
})) {

    companion object {
        // Cache resolved icon resource IDs — getIdentifier() is expensive, only call once per name
        private val iconCache = HashMap<String, Int>()

        fun resolveIcon(context: android.content.Context, providerName: String): Int {
            return iconCache.getOrPut(providerName) {
                val name = providerName.lowercase().replace(" ", "_")
                var id = context.resources.getIdentifier("icon_$name", "drawable", context.packageName)
                if (id == 0) id = context.resources.getIdentifier(name, "drawable", context.packageName)
                if (id == 0) id = context.resources.getIdentifier("${name}icon", "drawable", context.packageName)
                id // 0 if not found, that's fine
            }
        }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            BrowseListCompactBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: MainAPI, position: Int) {
        val binding = holder.view as? BrowseListCompactBinding ?: return

        binding.apply {
            browseText.text = item.name
            try {
                if (item.pluginContext != null && item.iconId != null && item.iconId != 0) {
                    browseIcon.setImageDrawable(item.pluginContext!!.getDrawable(item.iconId!!))
                } else {
                    val context = browseIcon.context
                    val iconRes = resolveIcon(context, item.name)
                    when {
                        iconRes != 0 -> browseIcon.setImageResource(iconRes)
                        item.iconId != null && item.iconId != 0 -> browseIcon.setImageResource(item.iconId!!)
                        else -> browseIcon.setImageResource(R.drawable.ic_baseline_code_24)
                    }
                }
            } catch (e: Exception) {
                browseIcon.setImageResource(R.drawable.ic_baseline_code_24)
            }
            
            if (item.iconFullScreen) {
                browseIcon.setPadding(0, 0, 0, 0)
            } else {
                val paddingDp = (12 * browseIcon.context.resources.displayMetrics.density).toInt()
                browseIcon.setPadding(paddingDp, paddingDp, paddingDp, paddingDp)
            }

            try {
                browseIconBackground.setCardBackgroundColor(
                    ContextCompat.getColor(
                        browseIconBackground.context,
                        item.iconBackgroundId
                    )
                )
            } catch (e: Exception) {
                // Fallback to a safe default if resource ID is invalid across APKs
                browseIconBackground.setCardBackgroundColor(
                    ContextCompat.getColor(browseIconBackground.context, R.color.primaryGrayBackground)
                )
            }
            browseBackground.setOnClickListener {
                activity?.navigate(
                    R.id.global_to_navigation_mainpage,
                    MainPageFragment.newInstance(item.name),
                    options = com.lagradost.quicknovel.MainActivity.navOptions
                )
            }
        }
    }
}