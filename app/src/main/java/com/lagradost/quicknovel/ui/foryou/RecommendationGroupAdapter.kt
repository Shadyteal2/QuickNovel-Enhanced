package com.lagradost.quicknovel.ui.foryou

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.databinding.ItemRecommendationGroupBinding
import com.lagradost.quicknovel.ui.foryou.recommendation.Recommendation
import com.lagradost.quicknovel.ui.foryou.recommendation.RecommendationGroup

class RecommendationGroupAdapter(private val onBookClick: (Recommendation) -> Unit) :
    ListAdapter<RecommendationGroup, RecommendationGroupAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemRecommendationGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(group: RecommendationGroup) {
            binding.groupTitle.text = group.title
            val bookAdapter = RecommendationBookAdapter(onBookClick)
            binding.groupRecycler.apply {
                adapter = bookAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                
                addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        if (e.action == MotionEvent.ACTION_DOWN) {
                            // Tell parents (including ViewPager2) NOT to intercept touches while interacting with carousel
                            rv.parent.requestDisallowInterceptTouchEvent(true)
                        }
                        return false
                    }
                    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
                })
                
                // Haptic feedback for "tick" on scroll
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    private var lastPosition = -1
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val firstVisible = layoutManager.findFirstVisibleItemPosition()
                        if (firstVisible != lastPosition) {
                            lastPosition = firstVisible
                            recyclerView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                })
            }
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
