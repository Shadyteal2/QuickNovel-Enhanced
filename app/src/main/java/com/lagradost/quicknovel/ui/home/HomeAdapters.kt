package com.lagradost.quicknovel.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.lagradost.quicknovel.databinding.HomeHeaderItemBinding
import com.lagradost.quicknovel.databinding.HomeHeroItemBinding
import com.lagradost.quicknovel.util.KineticTiltHelper
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.setImage

class HeroAdapter(private val clickCallback: (ResultCached) -> Unit) : RecyclerView.Adapter<HeroAdapter.HeroViewHolder>() {
    var heroItem: ResultCached? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val binding = HomeHeroItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        val item = heroItem ?: return
        holder.binding.apply {
            heroImage.setImage(item.poster)
            heroTitle.text = item.name
            root.setOnClickListener { clickCallback(item) }
            KineticTiltHelper.applyKineticTilt(root)
            
            // Bento Support
            val params = root.layoutParams
            if (params is StaggeredGridLayoutManager.LayoutParams) {
                params.isFullSpan = true
            }
        }
    }

    override fun getItemCount(): Int = if (heroItem == null) 0 else 1

    class HeroViewHolder(val binding: HomeHeroItemBinding) : RecyclerView.ViewHolder(binding.root)
}

class HeaderAdapter(private val title: String) : RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = HomeHeaderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.binding.headerTitle.text = title
        val params = holder.binding.root.layoutParams
        if (params is StaggeredGridLayoutManager.LayoutParams) {
            params.isFullSpan = true
        }
    }

    override fun getItemCount(): Int = 1

    class HeaderViewHolder(val binding: HomeHeaderItemBinding) : RecyclerView.ViewHolder(binding.root)
}
