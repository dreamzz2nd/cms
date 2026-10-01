package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nyamimo.app.R
import com.nyamimo.app.model.AnimeItem

class ContinueWatchingAdapter(
    private var items: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<ContinueWatchingAdapter.ContinueViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class ContinueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivContinuePoster)
        val tvTitle: TextView = view.findViewById(R.id.tvContinueTitle)
        val tvEpProgress: TextView = view.findViewById(R.id.tvContinueEpProgress)
        val tvBadge: TextView = view.findViewById(R.id.tvContinueBadge)
        val pbProgress: ProgressBar = view.findViewById(R.id.pbContinueProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContinueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_continue_watching, parent, false)
        return ContinueViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContinueViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title

        val epStr = if (item.episode.isNotEmpty()) item.episode else "1"
        val timeStr = if (item.watchDurationText.isNotEmpty()) {
            val currTime = item.watchDurationText.split("/").firstOrNull()?.trim() ?: ""
            if (currTime.isNotEmpty()) " ($currTime)" else ""
        } else ""

        holder.tvEpProgress.text = "Tonton sampai episode $epStr$timeStr"

        val progress = if (item.watchProgressPercent in 5..100) item.watchProgressPercent else 65
        holder.pbProgress.progress = progress

        Glide.with(holder.itemView.context)
            .load(item.img)
            .placeholder(R.drawable.card_dark_bg)
            .error(R.drawable.logo_nyamimo)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.ivPoster)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
