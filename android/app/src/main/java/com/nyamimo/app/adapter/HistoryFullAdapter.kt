package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nyamimo.app.R
import com.nyamimo.app.model.AnimeItem

sealed class HistoryListItem {
    data class Header(val title: String) : HistoryListItem()
    data class Anime(val anime: AnimeItem) : HistoryListItem()
}

class HistoryFullAdapter(
    private var rawItems: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit,
    private val onDeleteClick: (AnimeItem) -> Unit,
    private val onDownloadClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val displayItems = mutableListOf<HistoryListItem>()

    init {
        buildDisplayList()
    }

    fun updateData(newItems: List<AnimeItem>) {
        this.rawItems = newItems
        buildDisplayList()
        notifyDataSetChanged()
    }

    private fun buildDisplayList() {
        displayItems.clear()
        val groups = rawItems.groupBy { it.timeGroup.ifEmpty { "Sebelumnya" } }
        for ((groupTitle, items) in groups) {
            displayItems.add(HistoryListItem.Header(groupTitle))
            items.forEach { displayItems.add(HistoryListItem.Anime(it)) }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is HistoryListItem.Header -> 0
            is HistoryListItem.Anime -> 1
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tvHistoryGroupTitle)
    }

    inner class AnimeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivHistoryPoster)
        val tvTypeTag: TextView = view.findViewById(R.id.tvHistoryTypeTag)
        val tvEpBadge: TextView = view.findViewById(R.id.tvHistoryEpBadge)
        val pbProgress: ProgressBar = view.findViewById(R.id.pbHistoryWatchProgress)
        val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
        val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        val btnMore: ImageView = view.findViewById(R.id.btnHistoryMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_horizontal_card, parent, false)
            AnimeViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is HistoryListItem.Header -> {
                (holder as HeaderViewHolder).tvHeader.text = item.title
            }
            is HistoryListItem.Anime -> {
                val anime = item.anime
                val vh = holder as AnimeViewHolder

                vh.tvTitle.text = anime.title
                vh.tvDate.text = anime.watchDate.ifEmpty { "28/09/2026" }

                val isMovie = anime.type.equals("movie", ignoreCase = true) || anime.episode.equals("movie", ignoreCase = true)
                vh.tvTypeTag.text = if (isMovie) "Movie" else "Anime"

                val epText = if (isMovie) anime.watchDurationText.ifEmpty { "1:46:11" } else "E ${anime.episode.ifEmpty { "1" }}"
                vh.tvEpBadge.text = epText

                vh.pbProgress.progress = if (anime.watchProgressPercent > 0) anime.watchProgressPercent else 65

                Glide.with(vh.itemView.context)
                    .load(anime.img)
                    .placeholder(R.drawable.card_dark_bg)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(vh.ivPoster)

                vh.itemView.setOnClickListener {
                    onItemClick(anime)
                }

                vh.btnMore.setOnClickListener { v ->
                    val popup = PopupMenu(v.context, v)
                    popup.menu.add(0, 1, 0, "Putar Episode Ini")
                    popup.menu.add(0, 2, 1, "Unduh Episode")
                    popup.menu.add(0, 3, 2, "Hapus dari Riwayat")
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            1 -> onItemClick(anime)
                            2 -> onDownloadClick(anime)
                            3 -> onDeleteClick(anime)
                        }
                        true
                    }
                    popup.show()
                }
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size
}
