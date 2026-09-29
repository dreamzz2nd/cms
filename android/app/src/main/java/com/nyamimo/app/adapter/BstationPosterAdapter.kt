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

class BstationPosterAdapter(
    private var items: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<BstationPosterAdapter.PosterViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class PosterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivPoster3Col)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle3Col)
        val tvScore: TextView = view.findViewById(R.id.tvScore3Col)
        val tvEp: TextView = view.findViewById(R.id.tvEp3Col)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bstation_poster, parent, false)
        return PosterViewHolder(view)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvScore.text = if (item.score.isNotEmpty()) item.score else "8.5"

        if (item.episode.isNotEmpty()) {
            holder.tvEp.visibility = View.VISIBLE
            holder.tvEp.text = if (item.episode.startsWith("Ep")) item.episode else "Ep ${item.episode}"
        } else {
            holder.tvEp.visibility = View.GONE
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
