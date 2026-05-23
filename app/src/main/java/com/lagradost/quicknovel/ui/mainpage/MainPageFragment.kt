package com.lagradost.quicknovel.ui.mainpage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult

class MainPageFragment : Fragment() {
    private val viewModel: MainPageViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            background = null
            setContent {
                val apiName = requireArguments().getString("apiName")!!
                MainPageScreen(
                    viewModel = viewModel,
                    apiName = apiName,
                    onBack = {
                        if (viewModel.isInSearch.value == true) {
                            viewModel.switchToMain()
                        } else {
                            activity?.onBackPressed()
                        }
                    },
                    onNovelClick = { item ->
                        val act = activity
                        if (act is androidx.fragment.app.FragmentActivity) {
                            act.loadResult(item.url, item.apiName)
                        } else {
                            MainActivity.loadResult(item.url, item.apiName)
                        }
                    },
                    onNovelLongClick = { item ->
                        MainActivity.loadPreviewPage(item)
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val apiName = requireArguments().getString("apiName")!!

        val defMainCategory = arguments?.getInt("mainCategory", 0)
        val defOrderBy = arguments?.getInt("orderBy", 0)
        val defTag = arguments?.getInt("tag", 0)

        viewModel.init(
            apiName,
            defMainCategory,
            defOrderBy,
            defTag
        )
    }

    companion object {
        fun newInstance(
            apiName: String,
            mainCategory: Int? = null,
            orderBy: Int? = null,
            tag: Int? = null
        ): Bundle =
            Bundle().apply {
                putString("apiName", apiName)

                if (mainCategory != null)
                    putInt("mainCategory", mainCategory)
                if (orderBy != null)
                    putInt("orderBy", orderBy)
                if (tag != null)
                    putInt("tag", tag)
            }
    }
}