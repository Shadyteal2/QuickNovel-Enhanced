package com.lagradost.quicknovel.ui.updates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.databinding.FragmentUpdatesBinding
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.UpdateItem
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UpdatesFragment : Fragment() {

    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UpdatesAdapter
    private var baseGroupedMap = emptyMap<String, List<NovelUpdateGroup>>()
    private var currentUpdatesList = emptyList<UpdateItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpdatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = UpdatesAdapter(
            onItemClick = { item ->
                MainActivity.loadResult(item.novelUrl, item.apiName, startAction = 2, startChapterUrl = item.chapterUrl) 
            },
            onGroupClick = { group ->
                val newMap = baseGroupedMap.toMutableMap()
                for ((header, novelGroups) in newMap) {
                    val index = novelGroups.indexOf(group)
                    if (index != -1) {
                        val mutatedList = novelGroups.toMutableList()
                        mutatedList[index] = group.copy(isExpanded = !group.isExpanded)
                        newMap[header] = mutatedList
                        break
                    }
                }
                baseGroupedMap = newMap
                renderList()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(StickyHeaderDecoration(adapter))

        val db = AppDatabase.getDatabase(requireContext().applicationContext)
        val dao = db.updateDao()

        // Observe flow of updates
        val threshold = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        viewLifecycleOwner.lifecycleScope.launch {
            dao.getRecentUpdates(threshold).collectLatest { list ->
                currentUpdatesList = list
                val grouped = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    groupUpdates(list)
                }
                baseGroupedMap = grouped
                renderList()
                binding.swipeRefresh.isRefreshing = false
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.lagradost.quicknovel.sync.UpdatesSyncWorker>()
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build()
            val wm = androidx.work.WorkManager.getInstance(requireContext().applicationContext)
            wm.enqueueUniqueWork("UpdatesManualSync", androidx.work.ExistingWorkPolicy.REPLACE, syncRequest)
            wm.getWorkInfoByIdLiveData(syncRequest.id).observe(viewLifecycleOwner) { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        binding.cleanupUpdates.setOnClickListener {
            val options = arrayOf("Delete All Updates", "Delete older than 1 Day")
            androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
                .setTitle("Cleanup Updates")
                .setItems(options) { _, which ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (which == 0) {
                            dao.deleteAllUpdates()
                        } else {
                            val thresh = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                            dao.deleteOldUpdates(thresh)
                        }
                    }
                }
                .show()
        }

        binding.selectUpdates.setOnClickListener {
            val context = requireContext()
            val keys = com.lagradost.quicknovel.BaseApplication.Companion.getKeys(com.lagradost.quicknovel.RESULT_BOOKMARK_STATE) ?: emptyList()
            val novels = keys.mapNotNull { key ->
                val id = key.replaceFirst(com.lagradost.quicknovel.RESULT_BOOKMARK_STATE, com.lagradost.quicknovel.RESULT_BOOKMARK)
                com.lagradost.quicknovel.BaseApplication.Companion.getKey<com.lagradost.quicknovel.util.ResultCached>(id)
            }.sortedBy { it.name }

            if (novels.isEmpty()) {
                com.lagradost.quicknovel.CommonActivity.showToast("No bookmarks found")
                return@setOnClickListener
            }

            val items = novels.map { it.name }.toTypedArray()
            val checkedItems = novels.map { it.isSyncEnabled }.toBooleanArray()

            androidx.appcompat.app.AlertDialog.Builder(context, R.style.AlertDialogCustom)
                .setTitle("Enable Updates")
                .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("Save") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        var changed = false
                        for (i in novels.indices) {
                            if (novels[i].isSyncEnabled != checkedItems[i]) {
                                val updated = novels[i].copy(isSyncEnabled = checkedItems[i])
                                com.lagradost.quicknovel.BaseApplication.Companion.setKey(com.lagradost.quicknovel.RESULT_BOOKMARK, updated.id.toString(), updated)
                                changed = true
                            }
                        }
                        if (changed) {
                            com.lagradost.quicknovel.CommonActivity.showToast("Settings Updated")
                            val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.lagradost.quicknovel.sync.UpdatesSyncWorker>()
                                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                                .build()
                            androidx.work.WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("UpdatesManualSync", androidx.work.ExistingWorkPolicy.REPLACE, syncRequest)
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Adjust for Edge-to-Edge Navigation Bars (3-button gesture supports)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.cleanupUpdates) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + (88 * resources.displayMetrics.density).toInt()
            v.layoutParams = params
            insets
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.selectUpdates) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + (160 * resources.displayMetrics.density).toInt()
            v.layoutParams = params
            insets
        }
    }

    private fun groupUpdates(list: List<UpdateItem>): Map<String, List<NovelUpdateGroup>> {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val groupedMap = list.groupBy { item ->
            val date = Date(item.uploadDate)
            val diff = System.currentTimeMillis() - item.uploadDate
            when {
                diff < 24 * 60 * 60 * 1000L -> "Today"
                diff < 48 * 60 * 60 * 1000L -> "Yesterday"
                else -> dateFormat.format(date)
            }
        }
        
        val resultMap = mutableMapOf<String, List<NovelUpdateGroup>>()
        for ((header, items) in groupedMap) {
            val novelMap = items.groupBy { it.novelUrl }
            val novelGroups = mutableListOf<NovelUpdateGroup>()
            for ((url, novelItems) in novelMap) {
                val sortedItems = novelItems.sortedBy { it.id }
                val unreadItems = sortedItems.filter { item ->
                    if (item.chapterIndex == -1) return@filter true
                    val key = "${item.novelName}/${item.chapterIndex}"
                    val readAt = com.lagradost.quicknovel.BaseApplication.Companion.getKey<Long>(
                        com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT, 
                        key
                    )
                    readAt == null
                }
                if (unreadItems.isNotEmpty()) {
                    val first = unreadItems.first()
                    novelGroups.add(NovelUpdateGroup(url, first.novelName, first.posterUrl, first.apiName, unreadItems))
                }
            }
            if (novelGroups.isNotEmpty()) {
                resultMap[header] = novelGroups
            }
        }
        return resultMap
    }

    private fun renderList() {
        val result = mutableListOf<UpdatesListItem>()
        for ((header, groups) in baseGroupedMap) {
            result.add(UpdatesListItem.Header(header))
            for (group in groups) {
                result.add(UpdatesListItem.Group(group))
                if (group.isExpanded) {
                    for (chapter in group.chapters) {
                        result.add(UpdatesListItem.Chapter(chapter))
                    }
                }
            }
        }
        adapter.updateList(result)
    }

    override fun onResume() {
        super.onResume()
        if (currentUpdatesList.isNotEmpty()) {
            baseGroupedMap = groupUpdates(currentUpdatesList)
            renderList()
        }
        com.lagradost.quicknovel.BaseApplication.Companion.setKey("NEW_UPDATES_COUNT", 0)
        com.lagradost.quicknovel.CommonActivity.activity?.sendBroadcast(android.content.Intent("com.lagradost.quicknovel.UPDATES_REFRESH"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
