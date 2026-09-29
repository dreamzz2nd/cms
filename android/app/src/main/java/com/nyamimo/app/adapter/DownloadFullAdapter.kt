package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nyamimo.app.R
import com.nyamimo.app.model.AnimeItem

class DownloadFullAdapter(
    private var items: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit,
    private val onDeleteClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<DownloadFullAdapter.DownloadViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class DownloadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivDownloadPoster)
        val tvEpBadge: TextView = view.findViewById(R.id.tvDownloadEpBadge)
        val tvTitle: TextView = view.findViewById(R.id.tvDownloadTitle)
        val tvSizeStatus: TextView = view.findViewById(R.id.tvDownloadSizeStatus)
        val tvDate: TextView = view.findViewById(R.id.tvDownloadDate)
        val btnMore: ImageView = view.findViewById(R.id.btnDownloadMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_full_card, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.title
        holder.tvEpBadge.text = "E ${item.episode.ifEmpty { "1" }}"
        val size = if (item.downloadSize.isNotEmpty()) item.downloadSize else "185 MB"
        holder.tvSizeStatus.text = "$size • 1080p HD • Tersimpan di Perangkat"
        holder.tvDate.text = "Diunduh ${item.watchDate.ifEmpty { "28/09/2026" }}"

        Glide.with(holder.itemView.context)
            .load(item.img)
            .placeholder(R.drawable.card_dark_bg)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.ivPoster)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        holder.btnMore.setOnClickListener { v ->
            val popup = PopupMenu(v.context, v)
            popup.menu.add(0, 1, 0, "Putar Offline")
            popup.menu.add(0, 2, 1, "Hapus File Unduhan")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onItemClick(item)
                    2 -> onDeleteClick(item)
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = items.size
}
