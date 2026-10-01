package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.nyamimo.app.R
import com.nyamimo.app.util.ParallaxSlideItem

class ParallaxHeroBannerAdapter(
    private var slides: List<ParallaxSlideItem>,
    private val onSlideClick: (ParallaxSlideItem) -> Unit
) : RecyclerView.Adapter<ParallaxHeroBannerAdapter.SlideViewHolder>() {

    fun updateData(newSlides: List<ParallaxSlideItem>) {
        slides = newSlides
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parallax_hero_banner, parent, false)
        return SlideViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount(): Int = slides.size

    inner class SlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivBg: ImageView = itemView.findViewById(R.id.ivHeroParallaxBg)
        private val ivObject: ImageView = itemView.findViewById(R.id.ivHeroParallaxObject)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHeroTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvHeroSubtitle)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvHeroBadge)
        private val btnPlay: FrameLayout = itemView.findViewById(R.id.btnHeroPlayFloating)

        fun bind(slide: ParallaxSlideItem) {
            tvTitle.text = slide.title.ifEmpty { "Nyamimo Streaming" }
            tvSubtitle.text = slide.subtitle.ifEmpty { "Streaming Anime Subtitle Indonesia Bebas Iklan" }
            
            if (slide.badge.isNotEmpty()) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = slide.badge
            } else {
                tvBadge.visibility = View.GONE
            }

            // 1. Load Background Image
            if (slide.background_url.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(slide.background_url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_placeholder_banner)
                    .error(R.drawable.bg_placeholder_banner)
                    .into(ivBg)
            } else {
                ivBg.setImageResource(R.drawable.bg_placeholder_banner)
            }

            // 2. Load Foreground Character / Object PNG Cutout (if any)
            if (slide.object_url.isNotEmpty()) {
                ivObject.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(slide.object_url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(ivObject)
            } else {
                ivObject.visibility = View.GONE
            }

            // 3. Click Handlers
            val clickListener = View.OnClickListener {
                onSlideClick(slide)
            }
            itemView.setOnClickListener(clickListener)
            btnPlay.setOnClickListener(clickListener)
        }
    }
}
