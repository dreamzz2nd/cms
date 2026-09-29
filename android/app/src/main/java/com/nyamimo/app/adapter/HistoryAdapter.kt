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

class HistoryAdapter(
    private var items: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivHistoryThumb)
        val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
        val tvEp: TextView = view.findViewById(R.id.tvHistoryEp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_card, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvEp.text = if (item.episode.isNotEmpty()) "Ep ${item.episode}" else "Ditonton"

        Glide.with(holder.itemView.context)
            .load(item.img)
            .placeholder(R.drawable.card_dark_bg)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.ivThumb)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
