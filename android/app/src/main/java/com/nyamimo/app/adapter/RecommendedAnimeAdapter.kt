package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nyamimo.app.R
import com.nyamimo.app.model.AnimeItem

class RecommendedAnimeAdapter(
    private var items: List<AnimeItem>,
    private val isGrid: Boolean = false,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ROW = 0
        private const val TYPE_GRID = 1
    }

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGrid) TYPE_GRID else TYPE_ROW
    }

    inner class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivRecPoster)
        val tvTitle: TextView = view.findViewById(R.id.tvRecTitle)
        val tvEpTag: TextView = view.findViewById(R.id.tvRecEpTag)
        val tvGenre1: TextView = view.findViewById(R.id.tvRecGenre1)
        val tvGenre2: TextView = view.findViewById(R.id.tvRecGenre2)
        val tvViews: TextView = view.findViewById(R.id.tvRecViews)
    }

    inner class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivCardPoster)
        val tvTitle: TextView = view.findViewById(R.id.tvCardTitle)
        val tvScore: TextView = view.findViewById(R.id.tvCardScore)
        val tvEpisode: TextView = view.findViewById(R.id.tvCardEpisode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_GRID) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recommended_card, parent, false)
            GridViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recommended_anime, parent, false)
            RowViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        if (holder is GridViewHolder) {
            holder.tvTitle.text = item.title
            val scoreText = if (item.score.isNotEmpty()) "★ ${item.score}" else "★ 8.8"
            holder.tvScore.text = scoreText

            val epText = when {
                item.status.contains("complete", ignoreCase = true) -> "Tamat"
                item.episode.isNotEmpty() -> "${item.episode} Episode"
                else -> "Update"
            }
            holder.tvEpisode.text = epText

            if (item.img.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.img)
                    .placeholder(R.drawable.logo_nyamimo)
                    .centerCrop()
                    .into(holder.ivPoster)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        } else if (holder is RowViewHolder) {
            holder.tvTitle.text = item.title
            holder.tvEpTag.text = if (item.status.contains("complete", ignoreCase = true)) "Tamat" else "Ep ${item.episode.ifEmpty { "Update" }}"
            holder.tvViews.text = "Score ${item.score.ifEmpty { "8.8" }} • Nyamimo HD"

            if (item.genres.isNotEmpty()) {
                holder.tvGenre1.text = item.genres[0]
                if (item.genres.size > 1) {
                    holder.tvGenre2.visibility = View.VISIBLE
                    holder.tvGenre2.text = item.genres[1]
                } else {
                    holder.tvGenre2.visibility = View.GONE
                }
            } else {
                holder.tvGenre1.text = "Anime Populer"
                holder.tvGenre2.visibility = View.GONE
            }

            if (item.img.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.img)
                    .placeholder(R.drawable.logo_nyamimo)
                    .centerCrop()
                    .into(holder.ivPoster)
            }

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
