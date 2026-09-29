package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nyamimo.app.R
import com.nyamimo.app.model.AnimeItem

class AnimeGridAdapter(
    private var items: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<AnimeGridAdapter.AnimeViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class AnimeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivPoster)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvScore: TextView = view.findViewById(R.id.tvScore)
        val tvEpisode: TextView = view.findViewById(R.id.tvEpisode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_anime_card, parent, false)
        return AnimeViewHolder(view)
    }

    override fun onBindViewHolder(holder: AnimeViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvScore.text = if (item.score.isNotEmpty()) "★ ${item.score}" else "★ 8.0"

        if (item.episode.isNotEmpty()) {
            holder.tvEpisode.visibility = View.VISIBLE
            holder.tvEpisode.text = if (item.episode.startsWith("Ep")) item.episode else "Ep ${item.episode}"
        } else {
            holder.tvEpisode.visibility = View.GONE
        }

        Glide.with(holder.itemView.context)
            .load(item.img)
            .placeholder(R.drawable.card_dark_bg)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.ivPoster)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
