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
import com.lagradost.quicknovel.util.KineticTiltHelper
import coil3.load
import coil3.request.crossfade

class BrowseAdapter : NoStateAdapter<MainAPI>(BaseDiffCallback(itemSame = { a, b ->
    a.name == b.name
}, contentSame = { a, b ->
    a.name == b.name
})) {

    companion object {
        private val iconCache = HashMap<String, Int>()

        fun resolveIcon(context: android.content.Context, providerName: String): Int {
            return iconCache.getOrPut(providerName) {
                val name = providerName.lowercase().replace(" ", "_")
                if (name.contains("wuxiabox")) {
                    val id = context.resources.getIdentifier("icon_wuxiabox", "drawable", context.packageName)
                    if (id != 0) return@getOrPut id
                }
                
                var id = context.resources.getIdentifier("icon_$name", "drawable", context.packageName)
                if (id == 0) id = context.resources.getIdentifier(name, "drawable", context.packageName)
                if (id == 0) id = context.resources.getIdentifier("${name}icon", "drawable", context.packageName)
                id
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
            
            // Kinetic Evolution: Apply tactile feedback to source tiles
            KineticTiltHelper.applyKineticTilt(browseBackground)

            try {
                val context = browseIcon.context
                val iconData: Any = when {
                    item.pluginContext != null && item.iconId != null && item.iconId != 0 -> 
                        item.pluginContext!!.getDrawable(item.iconId!!) ?: R.drawable.ic_baseline_code_24
                    else -> {
                        val resId = resolveIcon(context, item.name)
                        if (resId != 0) resId 
                        else if (item.iconId != null && item.iconId != 0) item.iconId!!
                        else R.drawable.ic_baseline_code_24
                    }
                }

                browseIcon.load(iconData) {
                    crossfade(true)
                }
            } catch (e: Exception) {
                browseIcon.setImageResource(R.drawable.ic_baseline_code_24)
            }

            if (item.iconFullScreen) {
                browseIcon.setPadding(0, 0, 0, 0)
            } else {
                val paddingDp = (2 * browseIcon.context.resources.displayMetrics.density).toInt()
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