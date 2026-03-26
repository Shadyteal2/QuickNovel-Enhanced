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

        // FAB and sync logic moved to MainActivity for tab-specific scoping

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
    }

    override fun onPause() {
        super.onPause()
    }
}