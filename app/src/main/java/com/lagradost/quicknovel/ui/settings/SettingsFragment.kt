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
        return androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setContent {
                com.lagradost.quicknovel.ui.theme.QuickNovelTheme {
                    SettingsScreen(
                        onBack = { findNavController().popBackStack() },
                        onNavigateToSubSettings = { xmlRes, iconRes, titleRes ->
                            findNavController().navigate(
                                R.id.action_navigation_settings_to_subSettings,
                                Bundle().apply {
                                    putInt(SubSettingsFragment.XML_RES_ID, xmlRes)
                                    putInt(SubSettingsFragment.ICON_RES_ID, iconRes)
                                    putInt(SubSettingsFragment.TITLE_RES_ID, titleRes)
                                }
                            )
                        },
                        onNavigateToReadingStats = {
                            findNavController().navigate(R.id.navigation_reading_stats)
                        },
                        showAboutDialog = {
                            showAboutDialog()
                        },
                        onOpenSocialUrl = { url ->
                            openUrl(url)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Hide ActionBar to avoid double headlines
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
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

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Handle error
        }
    }
}