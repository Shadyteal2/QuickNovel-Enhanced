package com.lagradost.quicknovel.ui.neolists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.lagradost.quicknovel.ui.search.SearchViewModel
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme

class NeoListDetailFragment : Fragment() {

    private lateinit var viewModel: NeoListDetailViewModel
    private val searchViewModel: SearchViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[NeoListDetailViewModel::class.java]

        val listId = arguments?.getString("listId") ?: ""

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            background = null

            setContent {
                QuickNovelTheme {
                    NeoListDetailScreen(
                        listId = listId,
                        viewModel = viewModel,
                        searchViewModel = searchViewModel,
                        onBack = {
                            activity?.onBackPressedDispatcher?.onBackPressed()
                        }
                    )
                }
            }
        }
    }
}
