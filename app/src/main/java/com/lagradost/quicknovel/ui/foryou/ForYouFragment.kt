package com.lagradost.quicknovel.ui.foryou

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lagradost.quicknovel.databinding.FragmentForYouBinding
import com.lagradost.quicknovel.ui.foryou.recommendation.UserTasteProfile
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar

class ForYouFragment : Fragment() {
    private var _binding: FragmentForYouBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ForYouViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForYouBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.fixPaddingStatusbar(binding.forYouRootLinear)
        
        setupAdapters()

        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            if (profile.isWizardComplete) {
                showRecommendations()
            } else {
                showWizard()
            }
        }

        viewModel.recommendations.observe(viewLifecycleOwner) { groups ->
            (binding.recommendationContent.adapter as? RecommendationGroupAdapter)?.submitList(groups)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.stats.observe(viewLifecycleOwner) { (recs, indexed) ->
            binding.recommendationStats.text = "$recs recommendations • $indexed novels indexed"
        }

        binding.swipeRefresh.setOnRefreshListener {
            val providers = com.lagradost.quicknovel.util.Apis.apis.filter { it.hasMainPage }.joinToString(", ") { it.name }
            com.lagradost.quicknovel.CommonActivity.showToast(activity, "Indexing from: $providers", 0)
            viewModel.refreshRecommendations()
        }

        binding.btnGetStarted.setOnClickListener {
            viewModel.markWizardComplete()
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun setupAdapters() {
        // Wizard Adapter
        binding.wizardChipsRecycler.apply {
            layoutManager = com.google.android.flexbox.FlexboxLayoutManager(context).apply {
                flexDirection = com.google.android.flexbox.FlexDirection.ROW
                justifyContent = com.google.android.flexbox.JustifyContent.FLEX_START
            }
            adapter = WizardTagAdapter(com.lagradost.quicknovel.ui.foryou.recommendation.TagCategory.entries) { selected ->
                val current = viewModel.profile.value ?: UserTasteProfile.EMPTY
                val affinities = selected.map { com.lagradost.quicknovel.ui.foryou.recommendation.TagAffinity(it, 1.0f, 1.0f) }
                viewModel.saveProfile(current.copy(preferredTags = affinities))
                
                binding.btnGetStarted.isEnabled = selected.size >= 3
                binding.btnGetStarted.alpha = if (selected.size >= 3) 1.0f else 0.5f
            }
        }

        // Recommendations Adapter
        binding.recommendationContent.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            adapter = RecommendationGroupAdapter { rec ->
                // Record Interaction
                viewModel.recordInteraction(rec.novel.url, "CLICK")
                
                // Navigate to ResultFragment
                navigateToResult(rec.novel.url, rec.novel.apiName)
            }
        }
    }

    private fun navigateToResult(url: String, apiName: String) {
        val bundle = com.lagradost.quicknovel.ui.result.ResultFragment.newInstance(url, apiName)
        (activity as? com.lagradost.quicknovel.MainActivity)?.navigate(
            com.lagradost.quicknovel.R.id.global_to_navigation_results,
            bundle
        )
    }

    private fun showWizard() {
        binding.recommendationContent.visibility = View.GONE
        binding.wizardContent.visibility = View.VISIBLE
        binding.btnGetStarted.isEnabled = (viewModel.profile.value?.preferredTags?.size ?: 0) >= 3
        binding.btnGetStarted.alpha = if (binding.btnGetStarted.isEnabled) 1.0f else 0.5f
    }

    private fun showRecommendations() {
        binding.recommendationContent.visibility = View.VISIBLE
        binding.wizardContent.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
