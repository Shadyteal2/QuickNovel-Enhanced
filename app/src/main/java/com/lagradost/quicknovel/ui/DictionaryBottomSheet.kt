package com.lagradost.quicknovel.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.quicknovel.databinding.DictionaryBottomSheetBinding
import com.lagradost.quicknovel.databinding.DictionaryMeaningItemBinding
import com.lagradost.quicknovel.databinding.DictionaryDefinitionItemBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.util.DictionaryHelper
import com.lagradost.quicknovel.util.DictionaryResponse
import com.lagradost.quicknovel.util.DictionaryMeaning
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.CommonActivity.showToast
import kotlinx.coroutines.launch

class DictionaryBottomSheet : BottomSheetDialogFragment() {
    private var _binding: DictionaryBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val ARG_WORD = "arg_word"

        fun newInstance(word: String): DictionaryBottomSheet {
            return DictionaryBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORD, word)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DictionaryBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val word = arguments?.getString(ARG_WORD) ?: return
        fetchDefinition(word)
    }

    private fun fetchDefinition(word: String) {
        binding.dictionaryLoading.isVisible = true
        binding.dictionaryContentLayout.isVisible = false
        binding.dictionaryErrorLayout.isVisible = false

        lifecycleScope.launch {
            when (val resource = DictionaryHelper.fetchDefinition(word)) {
                is Resource.Success -> {
                    displayResult(resource.value)
                }
                is Resource.Failure -> {
                    displayError(resource.errorString ?: "Could not fetch definition")
                }
                else -> {}
            }
            binding.dictionaryLoading.isVisible = false
        }
    }

    private fun displayResult(results: List<DictionaryResponse>) {
        if (results.isEmpty()) {
            displayError("No definition found")
            return
        }

        val firstResult = results[0]
        binding.dictionaryContentLayout.isVisible = true
        binding.dictionaryWord.text = firstResult.word
        binding.dictionaryPhonetic.text = firstResult.phonetic ?: firstResult.phonetics?.firstOrNull { !it.text.isNullOrBlank() }?.text ?: ""
        binding.dictionaryPhonetic.isVisible = binding.dictionaryPhonetic.text.isNotEmpty()

        val audioUrl = firstResult.phonetics?.firstOrNull { !it.audio.isNullOrBlank() }?.audio
        binding.dictionaryAudioBtn.isVisible = !audioUrl.isNullOrBlank()
        binding.dictionaryAudioBtn.setOnClickListener {
            playAudio(audioUrl)
        }

        binding.dictionaryMeaningsContainer.removeAllViews()
        
        // Flatten all meanings from all dictionary entries for this word
        val allMeanings = results.flatMap { it.meanings ?: emptyList() }
        
        for (meaning in allMeanings) {
            addMeaningView(meaning)
        }
    }

    private fun addMeaningView(meaning: DictionaryMeaning) {
        val meaningBinding = DictionaryMeaningItemBinding.inflate(layoutInflater, binding.dictionaryMeaningsContainer, false)
        meaningBinding.meaningPos.text = meaning.partOfSpeech
        
        meaning.definitions?.forEach { definition ->
            val defBinding = DictionaryDefinitionItemBinding.inflate(layoutInflater, meaningBinding.definitionsContainer, false)
            defBinding.definitionText.text = "• ${definition.definition}"
            if (!definition.example.isNullOrBlank()) {
                defBinding.definitionExample.isVisible = true
                defBinding.definitionExample.text = "\"${definition.example}\""
            }
            meaningBinding.definitionsContainer.addView(defBinding.root)
        }

        binding.dictionaryMeaningsContainer.addView(meaningBinding.root)
    }

    private fun displayError(message: String) {
        binding.dictionaryErrorLayout.isVisible = true
        binding.dictionaryErrorText.text = message
    }

    private fun playAudio(url: String?) {
        if (url == null) return
        
        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(fullUrl)
                prepareAsync()
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ -> 
                    showToast("Error playing audio")
                    true 
                }
            }
        } catch (e: Exception) {
            showToast("Error initializing audio")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }

    override fun getTheme(): Int {
        return R.style.AppBottomSheetDialogTheme
    }
}
