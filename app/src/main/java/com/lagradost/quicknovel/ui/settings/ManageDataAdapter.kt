package com.lagradost.quicknovel.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.ManageDataItemBinding

enum class ManageDataType {
    Note,
    Alias
}

data class ManageDataItem(
    val id: String,
    val title: String,
    val content: String,
    val type: ManageDataType
)

class ManageDataAdapter(
    private val onDelete: (ManageDataItem) -> Unit,
    private val onClick: (ManageDataItem) -> Unit
) : ListAdapter<ManageDataItem, ManageDataAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ManageDataItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ManageDataItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ManageDataItem) {
            binding.manageDataTitle.text = item.title
            binding.manageDataContent.text = item.content
            
            val iconRes = if (item.type == ManageDataType.Note) {
                R.drawable.ic_baseline_edit_24
            } else {
                R.drawable.ic_baseline_person_24 // Assuming this exists, fallback later if not
            }
            binding.manageDataIcon.setImageResource(iconRes)

            binding.manageDataDelete.setOnClickListener {
                onDelete(item)
            }
            
            binding.root.setOnClickListener {
                onClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ManageDataItem>() {
        override fun areItemsTheSame(oldItem: ManageDataItem, newItem: ManageDataItem): Boolean {
            return oldItem.id == newItem.id && oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: ManageDataItem, newItem: ManageDataItem): Boolean {
            return oldItem == newItem
        }
    }
}
