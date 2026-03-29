package com.lagradost.quicknovel.ui.foryou

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.databinding.ItemRecommendationGroupBinding
import com.lagradost.quicknovel.ui.foryou.recommendation.Recommendation
import com.lagradost.quicknovel.ui.foryou.recommendation.RecommendationGroup

class RecommendationGroupAdapter(
    private val viewPool: RecyclerView.RecycledViewPool = RecyclerView.RecycledViewPool(),
    private val onBookClick: (Recommendation, android.widget.ImageView) -> Unit
) : ListAdapter<RecommendationGroup, RecommendationGroupAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemRecommendationGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        private val bookAdapter = RecommendationBookAdapter(onBookClick)
        
        init {
            binding.groupRecycler.apply {
                setHasFixedSize(true)
                adapter = bookAdapter
                setRecycledViewPool(viewPool)
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false).apply {
                    initialPrefetchItemCount = 5
                }
                
                // Optimized Touch Listener
                addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        if (e.action == MotionEvent.ACTION_DOWN) {
                            rv.parent.requestDisallowInterceptTouchEvent(true)
                        }
                        return false
                    }
                    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
                })
                
                // Optimized Scroll Listener
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    private var lastPosition = -1
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val lm = recyclerView.layoutManager as LinearLayoutManager
                        val firstVisible = lm.findFirstVisibleItemPosition()
                        if (firstVisible != lastPosition && firstVisible != -1) {
                            lastPosition = firstVisible
                            recyclerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                })
            }
        }

        fun bind(group: RecommendationGroup) {
            binding.groupTitle.text = group.title
            bookAdapter.submitList(group.recommendations)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemRecommendationGroupBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<RecommendationGroup>() {
        override fun areItemsTheSame(oldItem: RecommendationGroup, newItem: RecommendationGroup): Boolean =
            oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: RecommendationGroup, newItem: RecommendationGroup): Boolean =
            oldItem == newItem
    }
}
