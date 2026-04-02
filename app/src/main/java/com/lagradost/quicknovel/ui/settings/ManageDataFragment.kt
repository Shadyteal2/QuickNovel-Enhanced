package com.lagradost.quicknovel.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.removeKey
import com.lagradost.quicknovel.DataStore.removeKeys
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.NOVEL_REPLACEMENTS
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.databinding.FragmentManageDataBinding
import com.lagradost.quicknovel.ui.result.NoteWrapper
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val RESULT_USER_NOTE: String = "RESULT_USER_NOTE"

class ManageDataFragment : Fragment() {
    private lateinit var binding: FragmentManageDataBinding
    private lateinit var adapter: ManageDataAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentManageDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.manageDataToolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        activity?.fixPaddingStatusbar(binding.manageDataToolbar)
        binding.manageDataToolbar.setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        binding.manageDataToolbar.inflateMenu(R.menu.manage_data_menu)
        binding.manageDataToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear_all) {
                showClearAllConfirmation()
                return@setOnMenuItemClickListener true
            }
            false
        }

        adapter = ManageDataAdapter(
            onDelete = { item -> deleteItem(item) },
            onClick = { /* Could navigate to novel, but let's keep it simple for now */ }
        )

        binding.manageDataRecycler.layoutManager = LinearLayoutManager(context)
        binding.manageDataRecycler.adapter = adapter

        loadData()
    }

    private fun loadData() {
        binding.manageDataLoading.isVisible = true
        binding.manageDataEmpty.isVisible = false
        binding.manageDataRecycler.isVisible = false

        ioSafe {
            val context = context ?: return@ioSafe
            
            // 1. Gather all keys from both folders
            val noteKeys = context.getKeys(RESULT_USER_NOTE)
            val aliasKeys = context.getKeys(NOVEL_REPLACEMENTS)
            
            // 2. Extract unique loadIds using a HashSet to avoid redundant hits
            val uniqueIds = HashSet<String>()
            noteKeys.forEach { uniqueIds.add(it.removePrefix("$RESULT_USER_NOTE/")) }
            aliasKeys.forEach { uniqueIds.add(it.removePrefix("$NOVEL_REPLACEMENTS/")) }
            
            if (uniqueIds.isEmpty()) {
                withContext(Dispatchers.Main) {
                    binding.manageDataLoading.isVisible = false
                    binding.manageDataEmpty.isVisible = true
                }
                return@ioSafe
            }

            // 3. Fetch metadata for all unique IDs (Titles)
            val titlesMap = mutableMapOf<String, String>()
            uniqueIds.forEach { id ->
                val cached = context.getKey<ResultCached>(RESULT_BOOKMARK, id)
                    ?: context.getKey<ResultCached>(HISTORY_FOLDER, id)
                titlesMap[id] = cached?.name ?: "Unknown Novel (ID: $id)"
            }

            // 4. Create display items
            val displayItems = mutableListOf<ManageDataItem>()
            
            // Add Notes
            noteKeys.forEach { key ->
                val id = key.removePrefix("$RESULT_USER_NOTE/")
                val note = context.getKey<NoteWrapper>(key, null)?.note
                if (!note.isNullOrBlank()) {
                    displayItems.add(
                        ManageDataItem(
                            id = id,
                            title = titlesMap[id] ?: id,
                            content = "Note: $note",
                            type = ManageDataType.Note
                        )
                    )
                }
            }

            // Add Aliases
            aliasKeys.forEach { key ->
                val id = key.removePrefix("$NOVEL_REPLACEMENTS/")
                val aliasMap = context.getKey<Map<String, String>>(key, null)
                if (!aliasMap.isNullOrEmpty()) {
                    val aliasesText = aliasMap.entries.joinToString(", ") { "${it.key} \u2192 ${it.value}" }
                    displayItems.add(
                        ManageDataItem(
                            id = id,
                            title = titlesMap[id] ?: id,
                            content = "Aliases: $aliasesText",
                            type = ManageDataType.Alias
                        )
                    )
                }
            }

            // Sort by title
            displayItems.sortBy { it.title }

            withContext(Dispatchers.Main) {
                adapter.submitList(displayItems)
                binding.manageDataLoading.isVisible = false
                binding.manageDataRecycler.isVisible = true
            }
        }
    }

    private fun deleteItem(item: ManageDataItem) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Delete ${item.type}?")
            .setMessage("Are you sure you want to delete this ${item.type.toString().lowercase()} for \"${item.title}\"?")
            .setPositiveButton(R.string.delete) { _, _ ->
                ioSafe {
                    val context = context ?: return@ioSafe
                    val folder = if (item.type == ManageDataType.Note) RESULT_USER_NOTE else NOVEL_REPLACEMENTS
                    context.removeKey(folder, item.id)
                    withContext(Dispatchers.Main) {
                        loadData() // Refresh list
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showClearAllConfirmation() {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            .setTitle("Clear ALL Data?")
            .setMessage("This will permanently delete ALL notes and character aliases across every novel in your library. This action cannot be undone.")
            .setIcon(R.drawable.ic_baseline_warning_24) // Assuming warning exists
            .setPositiveButton("Wipe Everything") { _, _ ->
                ioSafe {
                    val context = context ?: return@ioSafe
                    context.removeKeys(RESULT_USER_NOTE)
                    context.removeKeys(NOVEL_REPLACEMENTS)
                    withContext(Dispatchers.Main) {
                        loadData()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
