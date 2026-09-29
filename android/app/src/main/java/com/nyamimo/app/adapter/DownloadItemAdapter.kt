package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nyamimo.app.R
import com.nyamimo.app.model.AnimeItem

class DownloadItemAdapter(
    private var items: List<AnimeItem>,
    private val onPlayClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<DownloadItemAdapter.DownloadViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class DownloadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivDownloadPoster)
        val tvTitle: TextView = view.findViewById(R.id.tvDownloadTitle)
        val tvEpisode: TextView = view.findViewById(R.id.tvDownloadEpisode)
        val btnPlay: Button = view.findViewById(R.id.btnPlayOffline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_anime, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        val epText = if (item.episode.isNotEmpty()) "Episode ${item.episode}" else "Episode 1"
        holder.tvEpisode.text = "$epText • Siap Offline"

        if (item.img.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.img)
                .placeholder(R.drawable.logo_nyamimo)
                .centerCrop()
                .into(holder.ivPoster)
        }

        holder.btnPlay.setOnClickListener {
            onPlayClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
