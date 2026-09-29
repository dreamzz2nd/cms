package com.nyamimo.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nyamimo.app.R
import com.nyamimo.app.model.EpisodeItem

class BstationEpisodeAdapter(
    private var episodes: List<EpisodeItem>,
    private var selectedIndex: Int = 0,
    private val onEpisodeClick: (EpisodeItem, Int) -> Unit
) : RecyclerView.Adapter<BstationEpisodeAdapter.EpisodeViewHolder>() {

    fun updateData(newEpisodes: List<EpisodeItem>, newIndex: Int = 0) {
        this.episodes = newEpisodes
        this.selectedIndex = newIndex
        notifyDataSetChanged()
    }

    fun setSelected(index: Int) {
        val oldIndex = this.selectedIndex
        this.selectedIndex = index
        notifyItemChanged(oldIndex)
        notifyItemChanged(index)
    }

    inner class EpisodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.cardEpisodeContainer)
        val tvNumber: TextView = view.findViewById(R.id.tvEpNumber)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bstation_episode, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val item = episodes[position]
        val epNum = if (item.number.isNotEmpty()) item.number else if (item.episode.isNotEmpty()) item.episode else "${position + 1}"
        holder.tvNumber.text = epNum

        val isSelected = (position == selectedIndex)
        if (isSelected) {
            holder.container.setBackgroundResource(R.drawable.badge_gold_bg)
            holder.tvNumber.setTextColor(Color.parseColor("#17171B"))
        } else {
            holder.container.setBackgroundResource(R.drawable.chip_genre_bg)
            holder.tvNumber.setTextColor(Color.parseColor("#FFFFFF"))
        }

        holder.itemView.setOnClickListener {
            setSelected(position)
            onEpisodeClick(item, position)
        }
    }

    override fun getItemCount(): Int = episodes.size
}
