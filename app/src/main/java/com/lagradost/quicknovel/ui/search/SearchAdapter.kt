package com.lagradost.quicknovel.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.databinding.SearchResultGridBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.ui.newSharedPool
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

class SearchAdapter(
    private val viewModel: SearchViewModel,
    private val resView: AutofitRecyclerView,
) :
    NoStateAdapter<SearchResponse>(BaseDiffCallback(itemSame = { a, b ->
        a.url == b.url
    }, contentSame = { a, b ->
        a == b
    })) {

    private var scrollListener: RecyclerView.OnScrollListener? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val context = recyclerView.context
        val imageLoader = coil3.SingletonImageLoader.get(context)
        
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
                        ?: recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                        ?: return
                    
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    val totalItems = itemCount
                    
                    // Preload next 10 items
                    for (i in (lastVisible + 1)..(lastVisible + 10)) {
                        if (i in 0 until totalItems) {
                            val item = getItemOrNull(i) ?: continue
                            val img = item.image ?: continue
                            if (img is UiImage.Image && img.url.isNotBlank()) {
                                val request = coil3.request.ImageRequest.Builder(context)
                                    .data(img.url)
                                    .build()
                                imageLoader.enqueue(request)
                            }
                        }
                    }
                }
            }
        }
        recyclerView.addOnScrollListener(scrollListener!!)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        scrollListener?.let {
            recyclerView.removeOnScrollListener(it)
        }
        scrollListener = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    companion object {
        val sharedPool =
            newSharedPool {
                setMaxRecycledViews(CONTENT, 10)
            }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SearchResultGridBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SearchResponse, position: Int) {
        val binding = holder.view as? SearchResultGridBinding ?: return
        binding.apply {
            val coverHeight: Int = (resView.itemWidth / 0.68).roundToInt()
                imageView.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                    setImage(item.image)

                setOnClickListener {
                    viewModel.load(item)
                }

                setOnLongClickListener { view ->
                    hideKeyboard(view)
                    viewModel.showMetadata(item)
                    return@setOnLongClickListener true
                }
            }
            imageText.text = item.name
        }
    }
}