package com.lagradost.quicknovel.ui.foryou

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme

class ForYouFragment : Fragment() {
    private val viewModel: ForYouViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            background = null
            setContent {
                QuickNovelTheme {
                    ForYouScreen(
                        viewModel = viewModel,
                        onBookClick = { url, apiName ->
                            viewModel.recordInteraction(url, "CLICK")
                            navigateToResult(url, apiName)
                        },
                        onRefresh = {
                            val providers = com.lagradost.quicknovel.util.Apis.apis.filter { it.hasMainPage }.joinToString(", ") { it.name }
                            com.lagradost.quicknovel.CommonActivity.showToast(activity, "Indexing from: $providers", 0)
                            viewModel.refreshRecommendations()
                        }
                    )
                }
            }
        }
    }

    private fun navigateToResult(url: String, apiName: String) {
        val bundle = com.lagradost.quicknovel.ui.result.ResultFragment.newInstance(url, apiName)
        (activity as? com.lagradost.quicknovel.MainActivity)?.navigate(
            com.lagradost.quicknovel.R.id.global_to_navigation_results,
            bundle,
            null,
            null
        )
    }
}
