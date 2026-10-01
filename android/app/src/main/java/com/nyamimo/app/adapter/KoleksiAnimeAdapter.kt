package com.nyamimo.app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nyamimo.app.R
import com.nyamimo.app.model.AnimeItem

class KoleksiAnimeAdapter(
    private var items: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<KoleksiAnimeAdapter.KoleksiViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class KoleksiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivKoleksiPoster)
        val tvTopRightBadge: TextView = view.findViewById(R.id.tvKoleksiTopRightBadge)
        val layoutDolbyBadge: LinearLayout = view.findViewById(R.id.layoutKoleksiDolbyBadge)
        val tvEpisodeInfo: TextView = view.findViewById(R.id.tvKoleksiEpisodeInfo)
        val tvTitle: TextView = view.findViewById(R.id.tvKoleksiTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KoleksiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_koleksi_anime_card, parent, false)
        return KoleksiViewHolder(view)
    }

    override fun onBindViewHolder(holder: KoleksiViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title

        // Episode info formatted like iQIYI / Nyamimo
        val epText = when {
            item.episode.isNotEmpty() -> {
                val cleanEp = item.episode.replace("Ep", "").replace("ep", "").trim()
                if (item.type.equals("Completed", ignoreCase = true) || item.status.equals("Completed", ignoreCase = true)) {
                    "Full $cleanEp Episode"
                } else if (cleanEp.isNotEmpty()) {
                    "$cleanEp Episode"
                } else {
                    "Episode Terbaru"
                }
            }
            item.type.isNotEmpty() -> item.type
            else -> "Full HD"
        }
        holder.tvEpisodeInfo.text = epText

        // Top Right Badge (Gratis / TOP 10 / VIP / Gratis Terbatas)
        when (position % 4) {
            0 -> {
                holder.tvTopRightBadge.visibility = View.VISIBLE
                holder.tvTopRightBadge.text = "Gratis"
                holder.tvTopRightBadge.setBackgroundResource(R.drawable.badge_green_gratis)
            }
            1 -> {
                holder.tvTopRightBadge.visibility = View.VISIBLE
                holder.tvTopRightBadge.text = "TOP 10"
                holder.tvTopRightBadge.setBackgroundResource(R.drawable.badge_green_gratis)
            }
            2 -> {
                holder.tvTopRightBadge.visibility = View.VISIBLE
                holder.tvTopRightBadge.text = "Gratis"
                holder.tvTopRightBadge.setBackgroundResource(R.drawable.badge_green_gratis)
            }
            else -> {
                holder.tvTopRightBadge.visibility = View.VISIBLE
                holder.tvTopRightBadge.text = "VIP"
                holder.tvTopRightBadge.setBackgroundColor(Color.parseColor("#E6A100"))
            }
        }

        // Dolby / HD badge logic
        if (position % 2 == 0 || position % 3 == 0) {
            holder.layoutDolbyBadge.visibility = View.VISIBLE
        } else {
            holder.layoutDolbyBadge.visibility = View.GONE
        }

        Glide.with(holder.itemView.context)
            .load(item.img)
            .placeholder(R.drawable.bg_placeholder_koleksi_dark)
            .error(R.drawable.bg_placeholder_koleksi_dark)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.ivPoster)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
