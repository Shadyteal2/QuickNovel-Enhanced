package com.lagradost.quicknovel.ui.settings

import android.os.Bundle
import android.view.View
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.toPx

/**
 * A generic fragment to display modular settings sections.
 * Loads the XML resource specified in the 'xml_res' argument.
 */
class SubSettingsFragment : BaseSettingsFragment() {

    companion object {
        const val XML_RES_ID = "xml_res"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val xmlRes = arguments?.getInt(XML_RES_ID) ?: R.xml.settings_appearance
        val titleRes = arguments?.getInt("title_res") ?: R.string.appearance
        val title = getString(titleRes)

        setPreferencesFromResource(xmlRes, rootKey)
        
        // Inject large Discovery-style headline at position 0
        preferenceScreen.addPreference(object : androidx.preference.Preference(requireContext()) {
            init {
                layoutResource = R.layout.layout_settings_header
                key = "header_discovery"
                order = -200 // Ensure it's absolutely at the top
                isSelectable = false
            }

            override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                // Set the specific layout parameters to remove horizontal margins if any
                holder.itemView.setPadding(0, 0, 0, 0)
                
                holder.findViewById(R.id.settings_header_back)?.setOnClickListener {
                    androidx.navigation.fragment.NavHostFragment.findNavController(this@SubSettingsFragment)
                        .popBackStack()
                }

                holder.findViewById(R.id.settings_header_title)?.let { view ->
                    (view as? android.widget.TextView)?.let { tv ->
                        tv.text = title
                        
                        // Entrance Animation: Slide up + Fade in
                        tv.alpha = 0f
                        tv.translationY = 50f
                        tv.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(500)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    }
                }
            }
        })

        activity?.title = title
        
        // Setup shared listeners (themes, backup, etc.)
        setupPreferenceListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide ActionBar to avoid double headlines
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
        
        // Note: Status bar padding is now handled via fitsSystemWindows in layout_settings_header 
        // to prevent double-padding or clipping.
        
        listView?.let { list ->
            list.isDrawingCacheEnabled = false
            // Premium simple decoration (dividers only, no boxes)
            list.addItemDecoration(com.lagradost.quicknovel.ui.custom.GroupedPreferenceDecoration())

            // Fix bottom padding for navigation pill (optimized)
            list.clipToPadding = false
            list.setPadding(0, 0, 0, 85.toPx)
        }
    }
}
