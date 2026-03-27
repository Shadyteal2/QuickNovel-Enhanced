package com.lagradost.quicknovel.ui.foryou

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.databinding.ItemChipTagBinding
import com.lagradost.quicknovel.ui.foryou.recommendation.TagCategory

class WizardTagAdapter(
    private val tags: List<TagCategory>,
    private val onSelectionChanged: (Set<TagCategory>) -> Unit
) : RecyclerView.Adapter<WizardTagAdapter.ViewHolder>() {

    private val selectedTags = mutableSetOf<TagCategory>()

    inner class ViewHolder(val binding: ItemChipTagBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemChipTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tag = tags[position]
        holder.binding.chipTag.apply {
            text = tag.displayName
            isChecked = tag in selectedTags
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedTags.add(tag)
                else selectedTags.remove(tag)
                onSelectionChanged(selectedTags)
            }
        }
    }

    override fun getItemCount(): Int = tags.size
}
