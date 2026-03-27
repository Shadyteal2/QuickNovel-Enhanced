package com.lagradost.quicknovel.ui.foryou

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.databinding.ItemRecommendationBookBinding
import com.lagradost.quicknovel.ui.foryou.recommendation.Recommendation
import com.lagradost.quicknovel.util.UIHelper.setImage

class RecommendationBookAdapter(private val onClick: (Recommendation) -> Unit) :
    ListAdapter<Recommendation, RecommendationBookAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemRecommendationBookBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemRecommendationBookBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            bookName.text = item.novel.name
            
            // Match Percentage
            val matchPct = (item.score * 100).toInt()
            bookMatchPct.text = "$matchPct% match"
            bookMatchPct.visibility = if (item.type == com.lagradost.quicknovel.ui.foryou.recommendation.RecommendationType.FOR_YOU) View.VISIBLE else View.GONE
            
            // Rating (scale 0-200 to 0-5)
            bookRating.text = item.novel.rating?.let { "%.1f".format(it / 200.0) } ?: ""
            bookRatingContainer.visibility = if (item.novel.rating != null) View.VISIBLE else View.GONE
            
            // Source
            bookSource.text = item.novel.apiName
            
            bookPoster.setImage(item.novel.posterUrl)
            
            // IMPORTANT: Removed RenderEffect blur from root to fix "blurry posters"
            // The glassmorphism is now achieved via cardBackgroundColor and stroke in XML
            
            root.setOnClickListener { onClick(item) }
            root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Recommendation>() {
        override fun areItemsTheSame(oldItem: Recommendation, newItem: Recommendation): Boolean =
            oldItem.novel.url == newItem.novel.url

        override fun areContentsTheSame(oldItem: Recommendation, newItem: Recommendation): Boolean =
            oldItem == newItem
    }
}
