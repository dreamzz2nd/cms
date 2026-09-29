package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nyamimo.app.R

data class TrendingTag(
    val title: String,
    val badge: String = "", // "TOP", "PANAS", "BARU"
    val badgeType: String = "" // "red", "orange", "blue"
)

class TrendingTagAdapter(
    private var tags: List<TrendingTag>,
    private val onTagClick: (TrendingTag) -> Unit
) : RecyclerView.Adapter<TrendingTagAdapter.TagViewHolder>() {

    fun updateData(newTags: List<TrendingTag>) {
        this.tags = newTags
        notifyDataSetChanged()
    }

    inner class TagViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTrendingTitle)
        val tvBadge: TextView = view.findViewById(R.id.tvTrendingBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trending_tag, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        val tag = tags[position]
        holder.tvTitle.text = tag.title

        if (tag.badge.isNotEmpty()) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = tag.badge
            when (tag.badgeType) {
                "orange" -> holder.tvBadge.setBackgroundResource(R.drawable.badge_trending_orange)
                "blue" -> holder.tvBadge.setBackgroundResource(R.drawable.badge_trending_blue)
                else -> holder.tvBadge.setBackgroundResource(R.drawable.badge_trending_red)
            }
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onTagClick(tag)
        }
    }

    override fun getItemCount(): Int = tags.size
}
