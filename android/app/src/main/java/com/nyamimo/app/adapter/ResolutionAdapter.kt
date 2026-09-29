package com.nyamimo.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nyamimo.app.R
import com.nyamimo.app.model.PlayerOption

class ResolutionAdapter(
    private var options: List<PlayerOption>,
    private var selectedIndex: Int = 0,
    private var isDarkMode: Boolean = false,
    private val onOptionClick: (PlayerOption, Int) -> Unit
) : RecyclerView.Adapter<ResolutionAdapter.ResolutionViewHolder>() {

    fun updateData(newOptions: List<PlayerOption>, defaultIndex: Int = 0) {
        this.options = newOptions
        this.selectedIndex = defaultIndex.coerceIn(0, (newOptions.size - 1).coerceAtLeast(0))
        notifyDataSetChanged()
    }

    fun setDarkMode(darkMode: Boolean) {
        this.isDarkMode = darkMode
        notifyDataSetChanged()
    }

    fun setSelected(index: Int) {
        val prev = selectedIndex
        selectedIndex = index.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        if (prev != selectedIndex) {
            notifyItemChanged(prev)
            notifyItemChanged(selectedIndex)
        }
    }

    inner class ResolutionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvChip: TextView = view.findViewById(R.id.tvResolutionChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResolutionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_resolution_chip, parent, false)
        return ResolutionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResolutionViewHolder, position: Int) {
        val opt = options[position]
        holder.tvChip.text = formatQualityTitle(opt.title)

        val isSelected = (position == selectedIndex)
        if (isSelected) {
            holder.tvChip.setBackgroundResource(R.drawable.badge_gold_bg)
            holder.tvChip.setTextColor(Color.parseColor("#17171B"))
        } else {
            if (isDarkMode) {
                holder.tvChip.setBackgroundResource(R.drawable.chip_genre_bg)
                holder.tvChip.setTextColor(Color.parseColor("#FFFFFF"))
            } else {
                holder.tvChip.setBackgroundResource(R.drawable.chip_server_unselected)
                holder.tvChip.setTextColor(Color.parseColor("#17171B"))
            }
        }

        holder.itemView.setOnClickListener {
            setSelected(position)
            onOptionClick(opt, position)
        }
    }

    private fun formatQualityTitle(rawTitle: String): String {
        val lower = rawTitle.lowercase()
        return when {
            lower.contains("1080p") -> "1080p Full HD"
            lower.contains("720p") -> "720p HD"
            lower.contains("480p") -> "480p SD"
            lower.contains("360p") -> "360p"
            lower.contains("4k") -> "4K Ultra HD"
            lower.isNotEmpty() -> rawTitle
            else -> "Server Auto"
        }
    }

    override fun getItemCount(): Int = options.size
}
