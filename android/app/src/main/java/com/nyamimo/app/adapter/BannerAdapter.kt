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

class BannerAdapter(
    private val items: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    inner class BannerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivBanner: ImageView = view.findViewById(R.id.ivBanner)
        val tvBannerTitle: TextView = view.findViewById(R.id.tvBannerTitle)
        val tvBannerScore: TextView = view.findViewById(R.id.tvBannerScore)
        val tvBannerTag: TextView = view.findViewById(R.id.tvBannerTag)
        val tvBannerEp: TextView = view.findViewById(R.id.tvBannerEp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_banner, parent, false)
        return BannerViewHolder(view)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        val item = items[position]
        holder.tvBannerTitle.text = item.title
        holder.tvBannerScore.text = if (item.score.isNotEmpty()) item.score else "8.5"
        holder.tvBannerTag.text = if (item.type.isNotEmpty()) item.type.uppercase() else "HOT ANIME"
        holder.tvBannerEp.text = if (item.episode.isNotEmpty()) "Episode ${item.episode}" else "Episode Baru Tersedia"

        Glide.with(holder.itemView.context)
            .load(item.img)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.ivBanner)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
