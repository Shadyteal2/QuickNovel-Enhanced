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
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.util.KineticTiltHelper

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

    private lateinit var heroAdapter: HeroAdapter
    private lateinit var headerAdapter: HeaderAdapter
    private lateinit var browseAdapter: BrowseAdapter
    private lateinit var concatAdapter: androidx.recyclerview.widget.ConcatAdapter

    private fun setupGridView() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context ?: return)
        val usePinterest = prefs.getBoolean("library_pinterest_bento", false)

        val spanCountLandscape = 8
        val spanCountPortrait = 4
        val orientation = resources.configuration.orientation
        val totalSpan = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else {
            spanCountPortrait
        }

        if (usePinterest) {
            // Pinterest Discovery: 2 column Masonry
            binding.homeBrowselist.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
        } else {
            // Discovery Density: 4 portrait, 8 landscape
            val layoutManager = GridLayoutManager(context, totalSpan)
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    // QN-Enhanced: Headers take full span
                    return if (position < 2) totalSpan else 1
                }
            }
            binding.homeBrowselist.layoutManager = layoutManager
        }
        if (binding.homeBrowselist.layoutAnimation == null) {
            binding.homeBrowselist.layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(context, com.lagradost.quicknovel.R.anim.grid_layout_animation)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupGridView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupGridView()
        
        heroAdapter = HeroAdapter { res ->
            loadResult(res.source, res.apiName)
        }
        headerAdapter = HeaderAdapter(getString(R.string.home_title_text)) // Using Explore text
        browseAdapter = BrowseAdapter()
        
        concatAdapter = androidx.recyclerview.widget.ConcatAdapter(heroAdapter, headerAdapter, browseAdapter)
        
        binding.homeBrowselist.apply {
            adapter = concatAdapter
            setHasFixedSize(true)
        }

        observe(viewModel.homeApis) { list ->
            browseAdapter.submitList(list)
        }

        observe(viewModel.latestHistory) { res ->
            heroAdapter.heroItem = res
        }

        activity?.fixPaddingStatusbar(binding.homeToolbar)
        com.lagradost.quicknovel.util.GlassHeaderHelper.applyGlassHeader(
            binding.homeToolbar,
            binding.homeBrowselist // Now scroll handled by RecyclerView directly
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateHistory()
    }

    override fun onPause() {
        super.onPause()
    }
}