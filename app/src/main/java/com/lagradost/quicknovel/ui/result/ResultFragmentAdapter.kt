package com.lagradost.quicknovel.ui.result

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.*

class ResultFragmentAdapter(
    private val tabIds: List<Int>,
    private val bindingGetter: (View, Int) -> Unit
) : RecyclerView.Adapter<ResultFragmentAdapter.TabViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val layoutRes = when (viewType) {
            0 -> R.layout.result_novel_tab
            1 -> R.layout.result_reviews_tab
            2 -> R.layout.result_related_tab
            3 -> R.layout.result_chapters_tab
            else -> R.layout.result_novel_tab
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        bindingGetter(holder.itemView, tabIds[position])
    }

    override fun getItemCount(): Int = tabIds.size

    override fun getItemViewType(position: Int): Int = tabIds[position]

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
