package com.lagradost.quicknovel.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.quicknovel.databinding.TranslationBottomSheetBinding
import com.lagradost.quicknovel.util.TranslationEnginesManager
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.UIHelper.clipboardHelper
import com.lagradost.quicknovel.util.UIHelper.dismissSafe
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.R

class TranslationBottomSheet(private val originalText: String) : BottomSheetDialogFragment() {
    private var _binding: TranslationBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TranslationBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.translationOriginalText.text = originalText
        
        val engine = TranslationEnginesManager.getActiveEngine(requireContext())
        binding.translationEngineName.text = engine?.name ?: "No engine"

        translate()

        binding.translationCopyBtn.setOnClickListener {
            clipboardHelper(txt(R.string.translation), binding.translationText.text.toString())
            this.dialog.dismissSafe(activity)
        }
    }

    private fun translate() = ioSafe {
        activity?.runOnUiThread {
            binding.translationLoading.visibility = View.VISIBLE
            binding.translationContentLayout.visibility = View.GONE
            binding.translationErrorLayout.visibility = View.GONE
        }

        val result = TranslationEnginesManager.translate(requireContext(), originalText)

        activity?.runOnUiThread {
            binding.translationLoading.visibility = View.GONE
            when (result) {
                is Resource.Success -> {
                    binding.translationContentLayout.visibility = View.VISIBLE
                    binding.translationText.text = result.value
                }
                is Resource.Failure -> {
                    binding.translationErrorLayout.visibility = View.VISIBLE
                    binding.translationErrorText.text = result.errorString ?: "Unknown Error"
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
