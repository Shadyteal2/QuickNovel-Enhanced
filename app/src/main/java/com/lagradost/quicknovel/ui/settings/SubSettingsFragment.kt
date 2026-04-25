package com.lagradost.quicknovel.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.toPx

/**
 * A generic fragment to display modular settings sections.
 * Loads the XML resource specified in the 'xml_res' argument.
 */
class SubSettingsFragment : BaseSettingsFragment() {

    companion object {
        const val XML_RES_ID = "xml_res"
        const val ICON_RES_ID = "icon_res"
        const val TITLE_RES_ID = "title_res"
        const val TRANSITION_NAME = "transition_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No special transitions, use navigation defaults (Slide)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val xmlRes = arguments?.getInt(XML_RES_ID) ?: R.xml.settings_appearance
        val titleRes = arguments?.getInt(TITLE_RES_ID) ?: R.string.appearance
        val iconRes = arguments?.getInt(ICON_RES_ID) ?: R.drawable.ic_baseline_color_lens_24
        val title = getString(titleRes)

        setPreferencesFromResource(xmlRes, rootKey)
        
        // Inject large Discovery-style headline at position 0
        preferenceScreen.addPreference(object : androidx.preference.Preference(requireContext()) {
            init {
                layoutResource = R.layout.layout_settings_header
                key = "header_discovery"
                order = -200 
                isSelectable = false
            }

            override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
                super.onBindViewHolder(holder)
                holder.itemView.setPadding(0, 0, 0, 0)
                
                holder.findViewById(R.id.settings_header_back)?.setOnClickListener {
                    androidx.navigation.fragment.NavHostFragment.findNavController(this@SubSettingsFragment)
                        .popBackStack()
                }

                holder.findViewById(R.id.settings_header_title)?.let { view ->
                    (view as? android.widget.TextView)?.let { tv ->
                        tv.text = title
                    }
                }

                holder.findViewById(R.id.settings_header_icon)?.let { view ->
                    (view as? android.widget.ImageView)?.let { iv ->
                        iv.setImageResource(iconRes)
                    }
                }
            }
        })

        activity?.title = title
        setupPreferenceListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Clear solid background to allow MainActivity's custom background to show through.
        view.background = null
        view.isClickable = true
        view.isFocusable = true
        
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
        
        listView?.let { list ->
            list.background = null
            list.isDrawingCacheEnabled = false
            list.addItemDecoration(com.lagradost.quicknovel.ui.custom.GroupedPreferenceDecoration())
            list.clipToPadding = false
            list.clipChildren = false
            list.setPadding(0, 0, 0, 120.toPx) // Increased padding for safer clearance
        }
    }

}
