package com.lagradost.quicknovel.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lagradost.quicknovel.BuildConfig
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import android.content.Intent
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.preference.PreferenceManager
import android.content.SharedPreferences
import com.lagradost.quicknovel.MainActivity


/**
 * Settings Dashboard Fragment.
 * Displays a premium Bento-style grid for navigation.
 * No longer inherits from PreferenceFragmentCompat to avoid lifecycle crashes with custom layouts.
 */
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.setting_bento_grid, container, false)
    }

    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide ActionBar to avoid double headlines
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()

        // Restore alpha to 1f in case we are returning from a sub-screen
        view.alpha = 1f

        // Ensure background is transparent so global app background shows through
        view.background = null
        
        // Apply status bar padding to the root or toolbar
        activity?.fixPaddingStatusbar(view)

        setupToolbar(view)
        setupBentoGrid(view)
        setupSocialChips(view)
        updateReadingStats(view)
        setupExperimentalAesthetics(view)
    }

    private fun setupExperimentalAesthetics(view: View) {
        val context = context ?: return
        val settings = PreferenceManager.getDefaultSharedPreferences(context)

        fun updateAura() {
            (activity as? MainActivity)?.updateGlobalAura()
        }

        // Initial update
        updateAura()

        // Listen for changes
        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _: SharedPreferences, key: String? ->
            if (key == getString(R.string.living_glass_key) || 
                key == getString(R.string.aura_intensity_key) || 
                key == getString(R.string.aura_palette_key) ||
                key == getString(R.string.aura_speed_key)) {
                (activity as? MainActivity)?.updateGlobalAura()
            }
        }
        settings.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val context = context ?: return
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        settings.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private fun setupToolbar(view: View) {
        view.findViewById<View>(R.id.settings_back)?.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupBentoGrid(view: View) {
        val navController = findNavController()

        // Appearance
        view.findViewById<View>(R.id.bento_appearance)?.setOnClickListener {
            view.animate().alpha(0f).setDuration(150).start()
            navController.navigate(R.id.action_navigation_settings_to_subSettings, Bundle().apply {
                putInt(SubSettingsFragment.XML_RES_ID, R.xml.settings_appearance)
                putInt("title_res", R.string.appearance)
            })
        }

        // Reader
        view.findViewById<View>(R.id.bento_reader)?.setOnClickListener {
            view.animate().alpha(0f).setDuration(150).start()
            navController.navigate(R.id.action_navigation_settings_to_subSettings, Bundle().apply {
                putInt(SubSettingsFragment.XML_RES_ID, R.xml.settings_general)
                putInt("title_res", R.string.reader)
            })
        }

        // Storage
        view.findViewById<View>(R.id.bento_storage)?.setOnClickListener {
            view.animate().alpha(0f).setDuration(150).start()
            navController.navigate(R.id.action_navigation_settings_to_subSettings, Bundle().apply {
                putInt(SubSettingsFragment.XML_RES_ID, R.xml.settings_storage)
                putInt("title_res", R.string.storage)
            })
        }

        // Advanced
        view.findViewById<View>(R.id.bento_advanced)?.setOnClickListener {
            view.animate().alpha(0f).setDuration(150).start()
            navController.navigate(R.id.action_navigation_settings_to_subSettings, Bundle().apply {
                putInt(SubSettingsFragment.XML_RES_ID, R.xml.settings_dev)
                putInt("title_res", R.string.advanced)
            })
        }

        // Vibe & Aura (Experimental)
        view.findViewById<View>(R.id.bento_vibe)?.setOnClickListener {
            view.animate().alpha(0f).setDuration(150).start()
            navController.navigate(R.id.action_navigation_settings_to_subSettings, Bundle().apply {
                putInt(SubSettingsFragment.XML_RES_ID, R.xml.settings_vibe)
                putInt("title_res", R.string.vibe_aura)
            })
        }
        
        // Spring Animations
        applySpringTouch(view.findViewById(R.id.bento_appearance))
        applySpringTouch(view.findViewById(R.id.bento_reader))
        applySpringTouch(view.findViewById(R.id.bento_storage))
        applySpringTouch(view.findViewById(R.id.bento_advanced))
        applySpringTouch(view.findViewById(R.id.bento_vibe))
        applySpringTouch(view.findViewById(R.id.hero_about_card))
        applySpringTouch(view.findViewById(R.id.reading_stats_card))

        // Hero Card - Stats Navigation
        view.findViewById<View>(R.id.reading_stats_card)?.setOnClickListener {
            navController.navigate(R.id.navigation_reading_stats)
        }


        // About Dialog
        view.findViewById<View>(R.id.hero_about_card)?.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        context?.let { ctx ->
            MaterialAlertDialogBuilder(ctx)
                .setTitle("NeoQN")
                .setMessage("A premium, high-performance novel reader designed for excellence.\n\nVersion: ${BuildConfig.VERSION_NAME}\n\nDedicated to providing a beautiful and seamless reading experience.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }


    private fun applySpringTouch(v: View?) {
        v?.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.98f).scaleY(0.98f).alpha(0.8f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start()
                }
            }
            false // Return false to allow click listeners to fire
        }
    }

    private fun setupSocialChips(view: View) {
        view.findViewById<View>(R.id.chip_discord)?.setOnClickListener {
            openUrl("https://discord.gg/uvFXvtS3u8")
        }
        view.findViewById<View>(R.id.chip_telegram)?.setOnClickListener {
            openUrl("https://t.me/+i9MSwgeoXzU0NTE1")
        }
        view.findViewById<View>(R.id.chip_github)?.setOnClickListener {
            openUrl("https://github.com/Shadyteal2/QuickNovel-Enhanced")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Handle error
        }
    }


    private fun updateReadingStats(view: View) {
        // Just set static title for clean UI/UX
        view.findViewById<TextView>(R.id.hero_stats_value)?.text = "Reading Status"
    }
}