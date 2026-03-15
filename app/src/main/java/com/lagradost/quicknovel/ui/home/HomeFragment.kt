package com.lagradost.quicknovel.ui.home

import android.content.res.Configuration
import android.view.View
import androidx.fragment.app.viewModels
import com.lagradost.quicknovel.databinding.FragmentHomeBinding
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.ui.BaseFragment
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.R

class HomeFragment : BaseFragment<FragmentHomeBinding>(
    BindingCreator.Inflate(FragmentHomeBinding::inflate)
) {
    private val viewModel: HomeViewModel by viewModels()

    override fun fixLayout(view: View) {
        val compactView = false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding?.homeBrowselist?.spanCount = spanCountLandscape
        } else {
            binding?.homeBrowselist?.spanCount = spanCountPortrait
        }
    }

    override fun onBindingCreated(binding: FragmentHomeBinding) {
        val browseAdapter = BrowseAdapter()
        binding.homeBrowselist.apply {
            adapter = browseAdapter
            // layoutManager = GridLayoutManager(context, 1)
            setHasFixedSize(true)
        }

        observe(viewModel.homeApis) { list ->
            browseAdapter.submitList(list)
        }

<<<<<<< HEAD
        val settingsManager = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val hasBackground = !settingsManager.getString(getString(com.lagradost.quicknovel.R.string.background_image_key), null).isNullOrBlank()
        if (hasBackground) {
            binding.homeRoot.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.homeToolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
=======
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
>>>>>>> 5bb838d8046f5d610a9d13067b2f6501ceb79b0c
        }

        activity?.fixPaddingStatusbar(binding.homeToolbar)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateHistory()
    }
}