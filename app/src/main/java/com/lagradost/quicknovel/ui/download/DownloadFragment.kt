package com.lagradost.quicknovel.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.img
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme

class DownloadFragment : Fragment() {
    private lateinit var viewModel: DownloadViewModel

    @androidx.compose.runtime.Immutable
    data class DownloadData(
        @JsonProperty("source")
        val source: String,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("author")
        val author: String?,
        @JsonProperty("posterUrl")
        val posterUrl: String?,
        //RATING IS FROM 0-100
        @JsonProperty("rating")
        val rating: Int?,
        @JsonProperty("peopleVoted")
        val peopleVoted: Int?,
        @JsonProperty("views")
        val views: Int?,
        @JsonProperty("synopsis")
        val synopsis: String?,
        @JsonProperty("tags")
        val tags: List<String>?,
        @JsonProperty("apiName")
        val apiName: String,
        /** Unix time ms */
        @JsonProperty("lastUpdated")
        val lastUpdated: Long?,
        /** Unix time ms */
        @JsonProperty("lastDownloaded")
        val lastDownloaded: Long?,
        // Import extensions
        @JsonProperty("filePath")
        val filePath: String? = null,
        @JsonProperty("formatType")
        val formatType: String? = null,
        @JsonProperty("hash")
        val hash: String? = null,
        @JsonProperty("bookmarkType")
        val bookmarkType: Int? = null,
    )

    @androidx.compose.runtime.Immutable
    data class DownloadDataLoaded(
        val source: String,
        val name: String,
        val author: String?,
        val posterUrl: String?,
        //RATING IS FROM 0-100
        val rating: Int?,
        val peopleVoted: Int?,
        val views: Int?,
        val synopsis: String?,
        val tags: List<String>?,
        val apiName: String,
        val readCount: Int,
        val downloadedCount: Long,
        val downloadedTotal: Long,
        val ETA: String,
        val state: DownloadState,
        val id: Int,
        val generating: Boolean,
        val lastUpdated: Long?,
        val lastDownloaded: Long?,
        val filePath: String? = null,
        val formatType: String? = null,
        val hash: String? = null,
        val bookmarkType: Int? = null,
        val lastChapterRead: Int = 0,
        val newChaptersAvailable: Int = 0
    ) {
        val image by lazy {
            if(isImported) {
                val bitmap = BookDownloader2Helper.getCachedBitmap(activity, apiName, author, name)
                if(bitmap != null) {
                    return@lazy UiImage.Bitmap(bitmap)
                }
            }
            img(posterUrl)
        }

        val isImported: Boolean get() = (apiName == IMPORT_SOURCE || apiName == IMPORT_SOURCE_PDF)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel = ViewModelProvider(activity ?: this)[DownloadViewModel::class.java]
        
        // As per the original onCreateView / onViewCreated logic, trigger a refresh on screen creation.
        viewModel.loadAllData(true)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            // Allow Custom AMOLED / Custom backgrounds set by MainActivity/parent views to shine through
            background = null

            setContent {
                QuickNovelTheme {
                    DownloadScreen(
                        viewModel = viewModel,
                        onBookClick = { card ->
                            MainActivity.loadResult(card.source, card.apiName)
                        },
                        onBookClickLoaded = { card ->
                            viewModel.readEpub(card)
                        },
                        onBookLongClick = { card ->
                            viewModel.showMetadata(card)
                        },
                        onBookLongClickLoaded = { card ->
                            viewModel.showMetadata(card)
                        },
                        onImportEpubClick = {
                            viewModel.importEpub()
                        },
                        onPdfToEpubClick = {
                            viewModel.openPdfToEpubConverter()
                        }
                    )
                }
            }
        }
    }
}
