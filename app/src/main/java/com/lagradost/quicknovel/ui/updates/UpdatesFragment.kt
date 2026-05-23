package com.lagradost.quicknovel.ui.updates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.R

class UpdatesFragment : Fragment() {

    private val viewModel: UpdatesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            background = null
            setContent {
                com.lagradost.quicknovel.ui.theme.QuickNovelTheme {
                    UpdatesScreen(
                        viewModel = viewModel,
                        onCleanupClick = { showCleanupDialog() },
                        onSelectClick = { showSelectDialog() },
                        onBack = { activity?.onBackPressedDispatcher?.onBackPressed() }
                    )
                }
            }
        }
    }

    private fun showCleanupDialog() {
        val options = arrayOf("Delete All Updates", "Delete older than 1 Day")
        AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Cleanup Updates")
            .setItems(options) { _, which ->
                if (which == 0) {
                    viewModel.deleteAllUpdates()
                } else {
                    viewModel.deleteOldUpdates()
                }
            }
            .show()
    }

    private fun showSelectDialog() {
        val context = requireContext()
        val novels = viewModel.getSyncableNovels()

        if (novels.isEmpty()) {
            CommonActivity.showToast("No bookmarks found")
            return
        }

        val items = novels.map { it.name }.toTypedArray()
        val checkedItems = novels.map { it.isSyncEnabled }.toBooleanArray()

        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle("Enable Updates")
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                viewModel.updateSyncSettings(novels, checkedItems)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        BaseApplication.setKey("NEW_UPDATES_COUNT", 0)
        CommonActivity.activity?.sendBroadcast(android.content.Intent("com.lagradost.quicknovel.UPDATES_REFRESH"))
    }
}
