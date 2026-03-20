package com.lagradost.quicknovel.ui.updates

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.db.UpdateItem
import coil3.load
import coil3.request.crossfade
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.MainActivity

data class NovelUpdateGroup(
    val novelUrl: String,
    val novelName: String,
    val posterUrl: String?,
    val apiName: String,
    val chapters: List<UpdateItem>,
    var isExpanded: Boolean = false
)

sealed class UpdatesListItem {
    data class Header(val date: String) : UpdatesListItem()
    data class Group(val group: NovelUpdateGroup) : UpdatesListItem()
    data class Chapter(val chapter: UpdateItem) : UpdatesListItem()
    data class MoreLabel(val count: Int, val group: NovelUpdateGroup) : UpdatesListItem()
}

class UpdatesAdapter(
    private val onItemClick: (UpdateItem) -> Unit,
    private val onGroupClick: (NovelUpdateGroup) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StickyHeaderDecoration.StickyHeaderInterface {

    private var items: List<UpdatesListItem> = emptyList()

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_GROUP = 1
        const val TYPE_CHAPTER = 2
        const val TYPE_MORE = 3
    }

    fun updateList(newItems: List<UpdatesListItem>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newItems[newItemPosition]
                return if (old is UpdatesListItem.Header && new is UpdatesListItem.Header) old.date == new.date
                else if (old is UpdatesListItem.Group && new is UpdatesListItem.Group) old.group.novelUrl == new.group.novelUrl
                else if (old is UpdatesListItem.Chapter && new is UpdatesListItem.Chapter) old.chapter.chapterUrl == new.chapter.chapterUrl
                else old is UpdatesListItem.MoreLabel && new is UpdatesListItem.MoreLabel && old.group.novelUrl == new.group.novelUrl
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newItems[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is UpdatesListItem.Header -> TYPE_HEADER
            is UpdatesListItem.Group -> TYPE_GROUP
            is UpdatesListItem.Chapter -> TYPE_CHAPTER
            is UpdatesListItem.MoreLabel -> TYPE_MORE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_update_header, parent, false))
            TYPE_GROUP -> GroupViewHolder(inflater.inflate(R.layout.item_update_row, parent, false))
            else -> ChapterViewHolder(inflater.inflate(R.layout.item_update_chapter, parent, false)) // Handles Chapter and MoreLabel
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is UpdatesListItem.Header -> (holder as HeaderViewHolder).title.text = item.date
            is UpdatesListItem.Group -> (holder as GroupViewHolder).bind(item.group, onGroupClick)
            is UpdatesListItem.Chapter -> (holder as ChapterViewHolder).bind(item.chapter, onItemClick)
            is UpdatesListItem.MoreLabel -> (holder as ChapterViewHolder).bindMore(item, onGroupClick)
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.headerTitle)
    }

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imageView)
        val novelName: TextView = view.findViewById(R.id.novelName)
        val chapterName: TextView = view.findViewById(R.id.chapterName)
        val expandIcon: ImageView = view.findViewById(R.id.expandIcon)
        val backgroundCard: View = view.findViewById(R.id.backgroundCard)
        val posterCard: View = view.findViewById(R.id.posterCard)

        fun bind(group: NovelUpdateGroup, onClick: (NovelUpdateGroup) -> Unit) {
            novelName.text = group.novelName
            chapterName.text = "Latest Chapter: ${group.chapters.firstOrNull()?.chapterName ?: ""}"
            
            expandIcon.rotation = if (group.isExpanded) 180f else 0f

            if (group.posterUrl != null) {
                image.load(group.posterUrl) {
                    crossfade(true)
                }
            } else {
                image.setImageResource(R.drawable.dot_bg)
            }

            backgroundCard.setOnClickListener {
                if (group.isExpanded) {
                    itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                } else {
                    itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onClick(group)
            }

            posterCard.setOnClickListener {
                MainActivity.loadResult(group.novelUrl, group.apiName)
            }
        }
    }

    class ChapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chapterName: TextView = view.findViewById(R.id.chapterName)
        val downloadIcon: ImageView = view.findViewById(R.id.downloadIcon)

        fun bind(item: UpdateItem, onClick: (UpdateItem) -> Unit) {
            chapterName.text = item.chapterName
            downloadIcon.visibility = View.VISIBLE
            itemView.setOnClickListener { onClick(item) }
        }

        fun bindMore(item: UpdatesListItem.MoreLabel, onClick: (NovelUpdateGroup) -> Unit) {
            chapterName.setTextColor(itemView.context.colorFromAttribute(com.lagradost.quicknovel.R.attr.colorPrimary))
            chapterName.text = "+${item.count} more"
            downloadIcon.visibility = View.GONE
            itemView.setOnClickListener {
                itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick(item.group)
            }
        }
    }

    // StickyHeaderInterface implementation
    override fun getHeaderPositionForItem(itemPosition: Int): Int {
        var pos = itemPosition
        while (pos >= 0) {
            if (isHeader(pos)) return pos
            pos--
        }
        return -1
    }

    override fun getHeaderLayout(headerPosition: Int): Int {
        return R.layout.item_update_header
    }

    override fun bindHeaderData(header: View, headerPosition: Int) {
        val title = header.findViewById<TextView>(R.id.headerTitle)
        val item = items[headerPosition]
        if (item is UpdatesListItem.Header) {
            title.text = item.date
        }
    }

    override fun isHeader(itemPosition: Int): Boolean {
        return items.getOrNull(itemPosition) is UpdatesListItem.Header
    }
}
