package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nyamimo.app.R
import com.nyamimo.app.model.MimoNewsItem

class MimoNewsAdapter(
    private var items: List<MimoNewsItem>,
    private val onItemClick: (MimoNewsItem) -> Unit
) : RecyclerView.Adapter<MimoNewsAdapter.NewsViewHolder>() {

    fun updateData(newItems: List<MimoNewsItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class NewsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvNewsTitle)
        val tvSubTitle: TextView = view.findViewById(R.id.tvNewsSubTitle)
        val tvPvBadge: TextView = view.findViewById(R.id.tvNewsPvBadge)
        val ivPoster: ImageView = view.findViewById(R.id.ivNewsPoster)
        val tvReleaseDate: TextView = view.findViewById(R.id.tvNewsReleaseDate)
        val tvEpisodes: TextView = view.findViewById(R.id.tvNewsEpisodes)
        val tvStudio: TextView = view.findViewById(R.id.tvNewsStudio)
        val tvSource: TextView = view.findViewById(R.id.tvNewsSource)
        val tvGenres: TextView = view.findViewById(R.id.tvNewsGenres)
        val tvSynopsis: TextView = view.findViewById(R.id.tvNewsSynopsis)
        val tvMembers: TextView = view.findViewById(R.id.tvNewsMembers)
        val tvLikes: TextView = view.findViewById(R.id.tvNewsLikes)
        val tvScore: TextView = view.findViewById(R.id.tvNewsScore)
        val btnDetail: TextView = view.findViewById(R.id.btnNewsDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mimo_news_card, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.title
        val sub = if (item.titleJapanese.isNotEmpty()) item.titleJapanese else item.titleEnglish
        if (sub.isNotEmpty()) {
            holder.tvSubTitle.visibility = View.VISIBLE
            holder.tvSubTitle.text = sub
        } else {
            holder.tvSubTitle.visibility = View.GONE
        }

        if (item.trailerUrl.isNotEmpty() || item.trailerEmbedUrl.isNotEmpty()) {
            holder.tvPvBadge.visibility = View.VISIBLE
        } else {
            holder.tvPvBadge.visibility = View.GONE
        }

        holder.tvReleaseDate.text = item.seasonYear.ifEmpty { item.releaseDate }
        holder.tvEpisodes.text = item.episodes.ifEmpty { "TV Series" }
        holder.tvStudio.text = "Studio: ${item.studio.ifEmpty { "Nyamimo Studio" }}"
        holder.tvSource.text = "Source: ${item.source.ifEmpty { "Manga" }}"

        if (item.genres.isNotEmpty()) {
            holder.tvGenres.visibility = View.VISIBLE
            holder.tvGenres.text = item.genres.take(3).joinToString(", ")
        } else {
            holder.tvGenres.visibility = View.GONE
        }

        holder.tvSynopsis.text = item.synopsis.ifEmpty { "Sinopsis segera diperbarui." }

        // Penonton / Members
        if (item.members.isNotEmpty()) {
            holder.tvMembers.visibility = View.VISIBLE
            holder.tvMembers.text = "👥 ${item.members}"
        } else {
            holder.tvMembers.visibility = View.GONE
        }

        // Like / Favorites
        if (item.favorites.isNotEmpty()) {
            holder.tvLikes.visibility = View.VISIBLE
            holder.tvLikes.text = "❤️ ${item.favorites}"
        } else {
            holder.tvLikes.visibility = View.GONE
        }

        // Score
        if (item.score.isNotEmpty() && item.score != "N/A") {
            holder.tvScore.visibility = View.VISIBLE
            holder.tvScore.text = "⭐ ${item.score}"
        } else {
            holder.tvScore.visibility = View.GONE
        }


        if (item.img.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(item.img)
                .placeholder(R.drawable.logo_nyamimo)
                .centerCrop()
                .into(holder.ivPoster)
        }

        val clickListener = View.OnClickListener {
            onItemClick(item)
        }

        holder.itemView.setOnClickListener(clickListener)
        holder.btnDetail.setOnClickListener(clickListener)
    }

    override fun getItemCount(): Int = items.size
}
