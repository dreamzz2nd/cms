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
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<RecommendedAnimeAdapter.RecViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class RecViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivRecPoster)
        val tvTitle: TextView = view.findViewById(R.id.tvRecTitle)
        val tvEpTag: TextView = view.findViewById(R.id.tvRecEpTag)
        val tvGenre1: TextView = view.findViewById(R.id.tvRecGenre1)
        val tvGenre2: TextView = view.findViewById(R.id.tvRecGenre2)
        val tvViews: TextView = view.findViewById(R.id.tvRecViews)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recommended_anime, parent, false)
        return RecViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecViewHolder, position: Int) {
        val item = items[position]
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

    override fun getItemCount(): Int = items.size
}
