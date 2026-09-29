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

class SearchSuggestionAdapter(
    private var animeList: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<SearchSuggestionAdapter.SuggestionViewHolder>() {

    fun updateData(newList: List<AnimeItem>) {
        this.animeList = newList
        notifyDataSetChanged()
    }

    inner class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImg: ImageView = view.findViewById(R.id.ivSuggestImg)
        val tvTitle: TextView = view.findViewById(R.id.tvSuggestTitle)
        val tvType: TextView = view.findViewById(R.id.tvSuggestType)
        val tvScore: TextView = view.findViewById(R.id.tvSuggestScore)
        val tvEpisode: TextView = view.findViewById(R.id.tvSuggestEpisode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_suggestion, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val item = animeList[position]
        holder.tvTitle.text = item.title
        holder.tvType.text = if (item.type.isNotEmpty()) item.type else "Anime"
        holder.tvScore.text = if (item.score.isNotEmpty()) "★ ${item.score}" else "★ 8.5"
        holder.tvEpisode.text = if (item.episode.isNotEmpty()) item.episode else "Ongoing"

        Glide.with(holder.itemView.context)
            .load(item.img)
            .placeholder(R.drawable.chip_genre_bg)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.ivImg)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = animeList.size
}
