package com.nyamimo.app.adapter

import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nyamimo.app.R
import com.nyamimo.app.model.AnimeItem
import java.net.URLEncoder

class BstationFeedAdapter(
    private var items: List<AnimeItem>,
    private val onItemClick: (AnimeItem) -> Unit
) : RecyclerView.Adapter<BstationFeedAdapter.FeedViewHolder>() {

    fun updateData(newItems: List<AnimeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class FeedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivFeedThumb)
        val vGradient: View = view.findViewById(R.id.vFeedGradient)
        val btnPlayTrailer: View = view.findViewById(R.id.btnPlayTrailer)
        val wvTrailer: WebView = view.findViewById(R.id.wvFeedTrailer)
        val tvLikes: TextView = view.findViewById(R.id.tvFeedLikes)
        val tvDuration: TextView = view.findViewById(R.id.tvFeedDuration)
        val tvTitle: TextView = view.findViewById(R.id.tvFeedTitle)
        val tvAuthor: TextView = view.findViewById(R.id.tvFeedAuthor)
        val btnDetail: TextView = view.findViewById(R.id.btnFeedDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bstation_feed, parent, false)
        return FeedViewHolder(view)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = "${item.title} - Official Trailer & Episodes"
        holder.tvLikes.text = if (item.score.isNotEmpty()) "★ ${item.score}" else "★ 8.9"
        holder.tvDuration.text = if (item.episode.isNotEmpty()) "Ep ${item.episode}" else "Trailer HD"
        holder.tvAuthor.text = "Nyamimo Official YouTube"

        // Reset YouTube webview visibility on recycled view
        holder.wvTrailer.visibility = View.GONE
        holder.ivThumb.visibility = View.VISIBLE
        holder.vGradient.visibility = View.VISIBLE
        holder.btnPlayTrailer.visibility = View.VISIBLE

        Glide.with(holder.itemView.context)
            .load(item.img)
            .placeholder(R.drawable.card_dark_bg)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.ivThumb)

        // YouTube Trailer Embed Click Handler
        holder.btnPlayTrailer.setOnClickListener {
            holder.ivThumb.visibility = View.GONE
            holder.vGradient.visibility = View.GONE
            holder.btnPlayTrailer.visibility = View.GONE
            holder.wvTrailer.visibility = View.VISIBLE

            val youtubeId = getYouTubeTrailerId(item.title)
            val embedUrl = if (youtubeId.isNotEmpty()) {
                "https://www.youtube.com/embed/$youtubeId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
            } else {
                val query = URLEncoder.encode("${item.title} anime official trailer", "UTF-8")
                "https://www.youtube.com/embed?listType=search&list=$query&autoplay=1&playsinline=1"
            }

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        body, html { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                        iframe { width:100%; height:100%; border:none; }
                    </style>
                </head>
                <body>
                    <iframe src="$embedUrl" allow="autoplay; fullscreen; encrypted-media" allowfullscreen></iframe>
                </body>
                </html>
            """.trimIndent()

            holder.wvTrailer.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            holder.wvTrailer.webChromeClient = WebChromeClient()
            holder.wvTrailer.webViewClient = WebViewClient()
            holder.wvTrailer.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
        }

        holder.btnDetail.setOnClickListener {
            onItemClick(item)
        }

        holder.tvTitle.setOnClickListener {
            onItemClick(item)
        }
    }

    private fun getYouTubeTrailerId(title: String): String {
        val t = title.lowercase()
        return when {
            t.contains("one piece") -> "MCb13lbREU0"
            t.contains("slime") || t.contains("tensura") -> "fF_l07mR7mQ"
            t.contains("naruto") -> "QczGo-n6578"
            t.contains("bleach") -> "e8YBesRKq_U"
            t.contains("mushoku tensei") -> "xVv6VbFp398"
            t.contains("shingeki") || t.contains("titan") -> "M_OauHnAFc8"
            t.contains("jujutsu") -> "pkKu9hLT-t8"
            t.contains("kimetsu") || t.contains("demon slayer") -> "VQGCKyvzIM4"
            t.contains("solo leveling") -> "vNm_7x_vF4c"
            t.contains("re:zero") || t.contains("rezero") -> "vfl0c5k4aW8"
            t.contains("chainsaw") -> "dFLX3U3F7w8"
            else -> ""
        }
    }

    override fun getItemCount(): Int = items.size
}
