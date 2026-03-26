package com.lagradost.quicknovel.ui.home

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lagradost.quicknovel.databinding.FragmentHomeBinding
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.sync.PluginSyncWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.widget.Toast

class HomeFragment : Fragment() {
    lateinit var binding: FragmentHomeBinding
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentHomeBinding.inflate(inflater)
        return binding.root
    }

    private fun setupGridView() {
        val compactView = false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.homeBrowselist.spanCount = spanCountLandscape
        } else {
            binding.homeBrowselist.spanCount = spanCountPortrait
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupGridView()
        val browseAdapter = BrowseAdapter()
        binding.homeBrowselist.apply {
            adapter = browseAdapter
            // layoutManager = GridLayoutManager(context, 1)
            setHasFixedSize(true)
        }

        // FAB is in activity_main.xml to float above the ViewPager2
        val syncFab = requireActivity().findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.home_sync_fab)
        syncFab?.visibility = View.VISIBLE
        syncFab?.setOnClickListener {
            context?.let { ctx ->
                Toast.makeText(ctx, "Checking for plugin updates...", Toast.LENGTH_SHORT).show()
                val syncRequest = OneTimeWorkRequestBuilder<PluginSyncWorker>()
                    .build()
                WorkManager.getInstance(ctx).enqueueUniqueWork(
                    "PluginSyncImmediate",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
            }
        }

        observe(viewModel.homeApis) { list ->
            browseAdapter.submitList(list)
        }

        observe(viewModel.latestHistory) { res ->
            if (res != null) {
                binding.homeHeroSection.visibility = View.VISIBLE
                binding.heroImage.setImage(res.poster)
                binding.heroTitle.text = res.name
                binding.homeHeroSection.setOnClickListener {
                    loadResult(res.source, res.apiName)
                }
            } else {
                binding.homeHeroSection.visibility = View.GONE
            }
        }

        activity?.fixPaddingStatusbar(binding.homeToolbar)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateHistory()
        // Show FAB when returning to Home tab
        requireActivity().findViewById<View>(R.id.home_sync_fab)?.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        // Hide FAB when leaving Home tab
        requireActivity().findViewById<View>(R.id.home_sync_fab)?.visibility = View.GONE
    }
}