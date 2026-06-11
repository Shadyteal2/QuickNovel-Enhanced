package com.lagradost.quicknovel.ui.mainpage

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.databinding.LoadingBottomBinding
import com.lagradost.quicknovel.databinding.SearchResultGridBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.ui.newSharedPool
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.UIHelper.setImage
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.widget.AutofitRecyclerView
import com.lagradost.quicknovel.util.KineticTiltHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

class MainAdapter(
    private val resView: AutofitRecyclerView,
    override var footers: Int
) : NoStateAdapter<SearchResponse>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
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

    override fun onBindFooter(holder: ViewHolderState<Any>) {
        (holder.view as LoadingBottomBinding).apply {
            val coverHeight: Int =
                (resView.itemWidth / 0.68).roundToInt()

            backgroundCard.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    coverHeight
                )
            }
            if (loadingItems)
                holder.view.resultLoading.startShimmer()
            else
                holder.view.resultLoading.stopShimmer()
        }

        holder.view.root.isVisible = loadingItems
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SearchResultGridBinding.inflate(
                LayoutInflater.from(
                    parent.context
                ), parent, false
            )
        )
    }

    override fun onCreateFooter(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            LoadingBottomBinding.inflate(
                LayoutInflater.from(
                    parent.context
                ), parent, false
            )
        )
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: SearchResponse,
        position: Int
    ) {
        (holder.view as SearchResultGridBinding).apply {
            val compactView = false//resView.context?.getGridIsCompact() ?: return

            val coverHeight: Int =
                if (compactView) 80.toPx else (resView.itemWidth / 0.64).roundToInt()

            imageView.apply {
                setImage(item.image)
                
                // QN-Enhanced: Apply premium tactile response
                KineticTiltHelper.applyKineticTilt(this)

                setOnClickListener {
                    transitionName = item.url
                    val extras = androidx.navigation.fragment.FragmentNavigatorExtras(this to item.url)
                    val act = com.lagradost.quicknovel.CommonActivity.activity
                    if (act is androidx.fragment.app.FragmentActivity) {
                        act.loadResult(item.url, item.apiName, 0, null, extras)
                    } else {
                        loadResult(item.url, item.apiName)
                    }
                }

                setOnLongClickListener { view ->
                    hideKeyboard(view)
                    MainActivity.loadPreviewPage(item)
                    return@setOnLongClickListener true
                }
            }

            imageText.text = item.name
        }
    }

    private var loadingItems: Boolean = false

    fun setLoading(to: Boolean) {
        if (loadingItems == to) return
        if (to) {
            loadingItems = true
            notifyItemRangeChanged(itemCount - footers, footers)
        } else {
            loadingItems = false
            notifyItemRangeChanged(itemCount - footers, footers)
        }
    }
}