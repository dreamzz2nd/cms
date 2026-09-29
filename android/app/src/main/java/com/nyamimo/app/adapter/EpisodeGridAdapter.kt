package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nyamimo.app.R
import com.nyamimo.app.model.EpisodeItem

class EpisodeGridAdapter(
    private val episodes: List<EpisodeItem>,
    private var selectedIndex: Int = 0,
    private val onEpisodeClick: (EpisodeItem, Int) -> Unit
) : RecyclerView.Adapter<EpisodeGridAdapter.EpisodeViewHolder>() {

    fun setSelected(index: Int) {
        val prev = selectedIndex
        selectedIndex = index
        notifyItemChanged(prev)
        notifyItemChanged(selectedIndex)
    }

    inner class EpisodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEpisodePill: TextView = view.findViewById(R.id.tvEpisodePill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_pill, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val ep = episodes[position]
        val epNum = if (ep.number.isNotEmpty()) ep.number else ep.episode
        holder.tvEpisodePill.text = if (epNum.startsWith("Ep")) epNum else "Ep $epNum"

        val isSelected = (position == selectedIndex)
        if (isSelected) {
            holder.tvEpisodePill.setBackgroundResource(R.drawable.episode_pill_active_bg)
            holder.tvEpisodePill.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.brand_black))
        } else {
            holder.tvEpisodePill.setBackgroundResource(R.drawable.episode_pill_bg)
            holder.tvEpisodePill.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
        }

        holder.itemView.setOnClickListener {
            setSelected(position)
            onEpisodeClick(ep, position)
        }
    }

    override fun getItemCount(): Int = episodes.size
}
