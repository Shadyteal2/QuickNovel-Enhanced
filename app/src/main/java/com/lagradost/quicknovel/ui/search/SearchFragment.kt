package com.lagradost.quicknovel.ui.search

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.MainActivity.Companion.navigate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.HomeEpisodesExpandedBinding
import com.lagradost.quicknovel.ui.home.HomeViewModel
import com.lagradost.quicknovel.ui.mainpage.MainPageFragment
import com.lagradost.quicknovel.ui.settings.showSearchProviders
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.applyGlassStyle

class SearchFragment : Fragment() {
    private val viewModel: SearchViewModel by activityViewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()

    companion object {
        var currentDialog: Dialog? = null

        fun loadHomepageList(viewModel: SearchViewModel, item: HomePageList) {
            if (currentDialog != null) return
            val act = activity ?: return

            val bottomSheetDialog = BottomSheetDialog(act, R.style.BottomSheetDrawerTheme)
            val binding = HomeEpisodesExpandedBinding.inflate(act.layoutInflater, null, false)
            bottomSheetDialog.setContentView(binding.root)

            binding.homeExpandedText.text = item.name
            binding.homeExpandedDragDown.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            // We will temporarily keep the old adapter logic for the bottom sheet 
            // since it's a small standalone component until we migrate it as well.
            binding.homeExpandedRecycler.apply {
                val searchAdapter = SearchAdapter(viewModel, binding.homeExpandedRecycler)
                searchAdapter.submitList(item.list)
                adapter = searchAdapter
                spanCount = 3
            }

            bottomSheetDialog.setOnDismissListener {
                currentDialog = null
            }
            currentDialog = bottomSheetDialog
            bottomSheetDialog.applyGlassStyle()
            bottomSheetDialog.show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        return ComposeView(requireContext()).apply {
            background = null
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuickNovelTheme {
                    SearchScreen(
                        viewModel = viewModel,
                        homeViewModel = homeViewModel,
                        onOpenProviders = {
                            showSearchProviders(requireContext())
                        },
                        onBookClick = { novel ->
                            view?.let { hideKeyboard(it) }
                            viewModel.load(novel)
                        },
                        onBookLongClick = { novel ->
                            view?.let { hideKeyboard(it) }
                            viewModel.showMetadata(novel)
                        },
                        onProviderClick = { apiName ->
                            activity?.navigate(
                                R.id.global_to_navigation_mainpage,
                                MainPageFragment.newInstance(apiName),
                                options = com.lagradost.quicknovel.MainActivity.navOptions
                            )
                        },
                        onAdvancedProviderMoreClick = { provider ->
                            loadHomepageList(viewModel, provider)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val viewPager = activity?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.main_viewpager)
                val isVisibleToUser = viewPager?.visibility == View.VISIBLE && viewPager.currentItem == 1
                val hasResponse = viewModel.searchResponse.value != null || viewModel.currentSearch.value != null
                if (isVisibleToUser && hasResponse) {
                    viewModel.clearSearch()
                    hideKeyboard(view)
                } else {
                    isEnabled = false
                    activity?.onBackPressedDispatcher?.onBackPressed()
                    isEnabled = true
                }
            }
        }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backCallback)
    }
}