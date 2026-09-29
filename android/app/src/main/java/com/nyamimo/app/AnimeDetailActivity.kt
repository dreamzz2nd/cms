package com.nyamimo.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.nyamimo.app.adapter.BstationEpisodeAdapter
import com.nyamimo.app.adapter.RecommendedAnimeAdapter
import com.nyamimo.app.adapter.ResolutionAdapter
import com.nyamimo.app.api.ApiClient
import com.nyamimo.app.databinding.ActivityAnimeDetailBinding
import com.nyamimo.app.model.AnimeDetailData
import com.nyamimo.app.model.AnimeItem
import com.nyamimo.app.model.EpisodeDataResponse
import com.nyamimo.app.model.EpisodeItem
import com.nyamimo.app.model.HomeResponse
import com.nyamimo.app.model.PlayerOption
import com.nyamimo.app.util.SessionManager

class AnimeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnimeDetailBinding
    private var exoPlayer: ExoPlayer? = null
    private var detailData: AnimeDetailData? = null
    private var slug: String = ""
    private var isLandscape = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private lateinit var bstationEpisodeAdapter: BstationEpisodeAdapter
    private lateinit var recommendedAnimeAdapter: RecommendedAnimeAdapter
    private lateinit var resolutionAdapter: ResolutionAdapter
    private lateinit var landscapeResolutionAdapter: ResolutionAdapter
    private var currentOptions: List<PlayerOption> = emptyList()
    private var currentActiveEpisode: EpisodeItem? = null

    private var isLiked = false
    private var isBookmarked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnimeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        slug = intent.getStringExtra("slug") ?: ""
        val previewTitle = ApiClient.cleanAnimeTitle(intent.getStringExtra("title") ?: "Nonton Anime")
        val previewScore = intent.getStringExtra("score") ?: "8.5"
        val previewSynopsis = intent.getStringExtra("synopsis") ?: ""
        val previewStatus = intent.getStringExtra("status") ?: "SEKARANG GRATIS"
        val previewTotalEps = intent.getStringExtra("episode") ?: ""

        // Set immediate preview information
        binding.tvDetailTitle.text = previewTitle
        binding.tvPlayerAnimeTitle.text = previewTitle
        binding.tvDetailScore.text = "Score $previewScore"
        binding.tvDetailStatus.text = if (previewStatus.contains("lengkap", ignoreCase = true) || previewStatus.contains("complete", ignoreCase = true)) "TAMAT" else "SEKARANG GRATIS"
        if (previewTotalEps.isNotEmpty()) {
            binding.tvDetailTotalEps.text = if (previewTotalEps.all { it.isDigit() }) "$previewTotalEps Episode" else previewTotalEps
        }

        binding.btnBackDetail.setOnClickListener {
            if (isLandscape || customView != null) {
                toggleFullscreen()
            } else {
                finish()
            }
        }

        binding.btnFullscreenToggle.setOnClickListener {
            toggleFullscreen()
        }

        binding.btnLandscapeServerToggle.setOnClickListener {
            if (binding.playerLandscapeServerBar.visibility == View.VISIBLE) {
                binding.playerLandscapeServerBar.visibility = View.GONE
            } else {
                binding.playerLandscapeServerBar.visibility = View.VISIBLE
            }
        }

        setupSubTabs()
        setupActionButtons()
        setupDetailSheetOpener()
        setupResolutionLists()
        setupEpisodeList()
        setupRecommendations()
        initExoPlayer()
        initWebView()
        applyFullscreenState(resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        loadDetailAndPlay()
        loadRecommendations()
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            binding.ivDetailLoadingGif.visibility = View.VISIBLE
            Glide.with(this).asGif().load(R.raw.loading_cat).into(binding.ivDetailLoadingGif)
        } else {
            binding.ivDetailLoadingGif.visibility = View.GONE
        }
    }

    private fun setupSubTabs() {
        binding.tabDetailInfo.setOnClickListener {
            // Already in info tab
        }

        binding.tabDetailKomentar.setOnClickListener {
            Toast.makeText(this, "Kolom komentar Nyamimo segera hadir", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupActionButtons() {
        binding.btnDetailLike.setOnClickListener {
            isLiked = !isLiked
            if (isLiked) {
                binding.ivDetailLikeIcon.setColorFilter(Color.parseColor("#EF4444"))
                binding.tvDetailLikeCount.text = "209.5K"
                binding.tvDetailLikeCount.setTextColor(Color.parseColor("#EF4444"))
                Toast.makeText(this, "Menyukai anime ini", Toast.LENGTH_SHORT).show()
            } else {
                binding.ivDetailLikeIcon.setColorFilter(Color.parseColor("#17171B"))
                binding.tvDetailLikeCount.text = "209.4K"
                binding.tvDetailLikeCount.setTextColor(Color.parseColor("#17171B"))
            }
        }

        binding.btnDetailBookmark.setOnClickListener {
            isBookmarked = !isBookmarked
            if (isBookmarked) {
                binding.ivDetailBookmarkIcon.setColorFilter(Color.parseColor("#FFCC00"))
                binding.tvDetailBookmarkCount.text = "771.3K"
                binding.tvDetailBookmarkCount.setTextColor(Color.parseColor("#FFCC00"))
                detailData?.let {
                    SessionManager.addWatchHistory(
                        this,
                        AnimeItem(
                            title = it.title,
                            slug = slug,
                            img = it.img,
                            score = it.score,
                            type = it.type,
                            status = "Favorite"
                        )
                    )
                }
                Toast.makeText(this, "Ditambahkan ke Favorit Nyamimo", Toast.LENGTH_SHORT).show()
            } else {
                binding.ivDetailBookmarkIcon.setColorFilter(Color.parseColor("#17171B"))
                binding.tvDetailBookmarkCount.text = "771.2K"
                binding.tvDetailBookmarkCount.setTextColor(Color.parseColor("#17171B"))
            }
        }

        binding.btnDetailDownload.setOnClickListener {
            val title = detailData?.title ?: binding.tvDetailTitle.text.toString()
            val epNum = currentActiveEpisode?.let { if (it.number.isNotEmpty()) it.number else it.episode } ?: "1"
            val poster = detailData?.img ?: (intent.getStringExtra("img") ?: "")
            val item = AnimeItem(
                title = title,
                slug = slug,
                img = poster,
                episode = epNum,
                score = detailData?.score ?: "8.5",
                type = detailData?.type ?: "TV Series",
                status = detailData?.status ?: "Completed",
                synopsis = detailData?.synopsis ?: ""
            )
            SessionManager.addDownload(this, item, epNum, "188 MB")
            Toast.makeText(this, "Berhasil mengunduh $title - Episode $epNum ke memori perangkat! Dapat ditonton offline di menu 'Unduhan Saya'.", Toast.LENGTH_LONG).show()
        }

        binding.btnDetailShare.setOnClickListener {
            val title = detailData?.title ?: binding.tvDetailTitle.text.toString()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Nonton $title di Nyamimo")
                putExtra(Intent.EXTRA_TEXT, "Ayo nonton $title di Nyamimo Anime Player!\nhttps://nyamimo.app/anime/$slug")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan anime ke..."))
        }
    }

    private fun setupDetailSheetOpener() {
        val clickListener = View.OnClickListener {
            showDetailModalSheet()
        }
        binding.rowTitleAndMore.setOnClickListener(clickListener)
        binding.tvDetailTitle.setOnClickListener(clickListener)
        binding.bannerRankRibbon.setOnClickListener(clickListener)
        binding.tvRankTitle.setOnClickListener(clickListener)
    }

    private fun showDetailModalSheet() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_anime_detail_sheet, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val ivPoster = dialogView.findViewById<ImageView>(R.id.ivSheetPoster)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvSheetTitle)
        val tvScore = dialogView.findViewById<TextView>(R.id.tvSheetScore)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvSheetStatus)
        val tvMeta = dialogView.findViewById<TextView>(R.id.tvSheetMeta)
        val tvGenres = dialogView.findViewById<TextView>(R.id.tvSheetGenres)
        val tvSynopsis = dialogView.findViewById<TextView>(R.id.tvSheetSynopsis)
        val btnCloseIcon = dialogView.findViewById<ImageView>(R.id.btnCloseDetailSheet)
        val btnClose = dialogView.findViewById<Button>(R.id.btnSheetClose)

        val data = detailData
        val title = data?.title ?: binding.tvDetailTitle.text.toString()
        tvTitle.text = title
        tvScore.text = if (data != null && data.score.isNotEmpty()) "Score ${data.score}" else binding.tvDetailScore.text
        tvStatus.text = if (data != null && data.status.isNotEmpty()) data.status else "Tersedia"

        val meta = "Studio: ${data?.studio?.ifEmpty { "Nyamimo Studio" } ?: "Nyamimo Studio"} • Musim: ${data?.season?.ifEmpty { "2024" } ?: "2024"} • Durasi: ${data?.duration?.ifEmpty { "24m" } ?: "24m"}"
        tvMeta.text = meta

        val genresStr = if (data != null && data.genreNames.isNotEmpty()) {
            data.genreNames.joinToString(", ")
        } else {
            "Action, Comedy, Fantasy, Super Power"
        }
        tvGenres.text = genresStr

        val synopsisText = if (data != null && data.synopsis.isNotEmpty()) {
            data.synopsis
        } else {
            "Anime seru dengan petualangan menarik yang dapat ditonton secara gratis dengan kualitas terbaik di Nyamimo."
        }
        tvSynopsis.text = synopsisText

        val posterUrl = data?.img ?: ""
        if (posterUrl.isNotEmpty()) {
            Glide.with(this)
                .load(posterUrl)
                .placeholder(R.drawable.logo_nyamimo)
                .centerCrop()
                .into(ivPoster)
        }

        btnCloseIcon.setOnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun setupEpisodeList() {
        binding.rvDetailEpisodes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bstationEpisodeAdapter = BstationEpisodeAdapter(emptyList(), 0) { epItem, _ ->
            playEpisode(epItem)
        }
        binding.rvDetailEpisodes.adapter = bstationEpisodeAdapter
    }

    private fun setupRecommendations() {
        binding.rvRecommendedAnime.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recommendedAnimeAdapter = RecommendedAnimeAdapter(emptyList()) { item ->
            val intent = Intent(this, AnimeDetailActivity::class.java).apply {
                putExtra("slug", item.slug)
                putExtra("title", item.title)
                putExtra("score", item.score)
                putExtra("status", item.status)
                putExtra("type", item.type)
                putExtra("episode", item.episode)
                putExtra("synopsis", item.synopsis)
            }
            startActivity(intent)
            finish()
        }
        binding.rvRecommendedAnime.adapter = recommendedAnimeAdapter
    }

    private fun loadRecommendations() {
        ApiClient.getHome(object : ApiClient.Callback<HomeResponse> {
            override fun onSuccess(result: HomeResponse) {
                val list = mutableListOf<AnimeItem>()
                list.addAll(result.popular.take(6))
                if (list.size < 6) {
                    list.addAll(result.ongoing.take(6 - list.size))
                }
                recommendedAnimeAdapter.updateData(list)
            }

            override fun onError(error: String) {
                // Silently fallback
            }
        })
    }

    private fun setupResolutionLists() {
        // 1. Portrait Resolution List
        binding.rvDetailResolutions.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        resolutionAdapter = ResolutionAdapter(emptyList(), 0, isDarkMode = false) { option, index ->
            currentActiveEpisode?.let { ep ->
                val epNum = if (ep.number.isNotEmpty()) ep.number else ep.episode
                resolutionAdapter.setSelected(index)
                landscapeResolutionAdapter.setSelected(index)
                switchResolution(option, detailData?.title ?: binding.tvDetailTitle.text.toString(), epNum)
            }
        }
        binding.rvDetailResolutions.adapter = resolutionAdapter

        // 2. Landscape Resolution List (Floating top bar)
        binding.rvLandscapeResolutions.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        landscapeResolutionAdapter = ResolutionAdapter(emptyList(), 0, isDarkMode = true) { option, index ->
            currentActiveEpisode?.let { ep ->
                val epNum = if (ep.number.isNotEmpty()) ep.number else ep.episode
                resolutionAdapter.setSelected(index)
                landscapeResolutionAdapter.setSelected(index)
                binding.playerLandscapeServerBar.visibility = View.GONE
                switchResolution(option, detailData?.title ?: binding.tvDetailTitle.text.toString(), epNum)
            }
        }
        binding.rvLandscapeResolutions.adapter = landscapeResolutionAdapter
    }

    private fun initExoPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,
                8000,
                500,
                1000
            )
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> showLoading(true)
                            Player.STATE_READY -> showLoading(false)
                            Player.STATE_ENDED -> showLoading(false)
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        showLoading(false)
                        if (currentOptions.size > 1) {
                            val nextOpt = currentOptions.getOrNull(1)
                            if (nextOpt != null) {
                                val epNum = currentActiveEpisode?.let { if (it.number.isNotEmpty()) it.number else it.episode } ?: "1"
                                switchResolution(nextOpt, detailData?.title ?: "", epNum)
                            }
                        }
                    }
                })
            }
        binding.detailPlayerView.player = exoPlayer
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        binding.detailPlayerWebView.setBackgroundColor(Color.BLACK)
        binding.detailPlayerWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(binding.detailPlayerWebView, true)
        } catch (e: Exception) {
            // Ignore
        }

        binding.detailPlayerWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }

        binding.detailPlayerWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                showLoading(false)
            }
        }

        binding.detailPlayerWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 40) {
                    showLoading(false)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                binding.playerContainer.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                binding.detailPlayerWebView.visibility = View.GONE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                applyFullscreenState(true)
            }

            override fun onHideCustomView() {
                if (customView == null) return
                binding.playerContainer.removeView(customView)
                customView = null
                binding.detailPlayerWebView.visibility = View.VISIBLE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                applyFullscreenState(false)
                customViewCallback?.onCustomViewHidden()
            }
        }
    }

    private fun loadDetailAndPlay() {
        if (slug.isEmpty()) return
        showLoading(true)

        ApiClient.getAnimeDetail(slug, object : ApiClient.Callback<AnimeDetailData> {
            override fun onSuccess(result: AnimeDetailData) {
                detailData = result
                val cleanTitle = ApiClient.cleanAnimeTitle(result.title)

                binding.tvDetailTitle.text = cleanTitle
                binding.tvPlayerAnimeTitle.text = cleanTitle
                binding.tvDetailScore.text = if (result.score.isNotEmpty()) "Score ${result.score}" else "Score 8.5"
                binding.tvDetailTotalEps.text = "${result.episodes.size} Episode"

                val rawEpisodes = result.episodes
                if (rawEpisodes.isNotEmpty()) {
                    // Sort episodes ASCENDING (Episode 1, 2, 3 ... onwards)
                    val episodes = rawEpisodes.sortedWith(compareBy { item ->
                        val num = item.number.toIntOrNull() ?: item.episode.toIntOrNull()
                        val match = Regex("""\b(?:Episode|Eps|Ep)\s*(\d+)""", RegexOption.IGNORE_CASE).find(item.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        num ?: match ?: 9999
                    })

                    val firstEpIndex = 0
                    val firstEp = episodes[0]

                    bstationEpisodeAdapter.updateData(episodes, firstEpIndex)
                    binding.rvDetailEpisodes.scrollToPosition(0)

                    playEpisode(firstEp)
                } else {
                    showLoading(false)
                    Toast.makeText(this@AnimeDetailActivity, "Episode belum tersedia", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                showLoading(false)
                Toast.makeText(this@AnimeDetailActivity, error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun playEpisode(episode: EpisodeItem) {
        currentActiveEpisode = episode
        val epNum = if (episode.number.isNotEmpty()) episode.number else episode.episode
        val title = detailData?.title ?: binding.tvDetailTitle.text.toString()

        binding.tvPlayerActiveEpisode.text = "Episode $epNum"
        showLoading(true)

        SessionManager.addWatchHistory(
            this,
            AnimeItem(
                title = title,
                slug = slug,
                img = detailData?.img ?: "",
                episode = epNum,
                score = detailData?.score ?: "8.8",
                type = detailData?.type ?: "TV Series",
                status = "Watching",
                synopsis = detailData?.synopsis ?: ""
            )
        )

        val detailEps = if (episode.detailEps.isNotEmpty()) episode.detailEps else episode.link

        ApiClient.getEpisodeData(detailEps, title, epNum, object : ApiClient.Callback<EpisodeDataResponse> {
            override fun onSuccess(result: EpisodeDataResponse) {
                currentOptions = result.videos
                if (currentOptions.isNotEmpty()) {
                    binding.serverSectionLayout.visibility = View.VISIBLE
                    val activeIndex = currentOptions.indexOfFirst { it.title == result.activeServerTitle }.coerceAtLeast(0)
                    resolutionAdapter.updateData(currentOptions, activeIndex)
                    landscapeResolutionAdapter.updateData(currentOptions, activeIndex)
                }

                renderStream(result)
            }

            override fun onError(error: String) {
                showLoading(false)
                Toast.makeText(this@AnimeDetailActivity, error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun switchResolution(option: PlayerOption, title: String, ep: String) {
        showLoading(true)
        Toast.makeText(this, "Server: ${option.title}", Toast.LENGTH_SHORT).show()

        ApiClient.resolveVideoOption(option, ep, title, currentOptions, object : ApiClient.Callback<EpisodeDataResponse> {
            override fun onSuccess(result: EpisodeDataResponse) {
                val activeIndex = currentOptions.indexOfFirst { it.title == option.title }.coerceAtLeast(0)
                resolutionAdapter.updateData(currentOptions, activeIndex)
                landscapeResolutionAdapter.updateData(currentOptions, activeIndex)
                renderStream(result)
            }

            override fun onError(error: String) {
                showLoading(false)
                Toast.makeText(this@AnimeDetailActivity, error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun renderStream(result: EpisodeDataResponse) {
        val videoUrl = result.videoURL
        val rawIframe = result.rawIframe
        val isDirect = result.isDirectVideo || videoUrl.endsWith(".mp4") || videoUrl.endsWith(".m3u8") || videoUrl.contains("googlevideo.com") || videoUrl.contains("storage.googleapis.com")

        if (isDirect && videoUrl.isNotEmpty()) {
            binding.detailPlayerWebView.visibility = View.GONE
            binding.detailPlayerView.visibility = View.VISIBLE

            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
        } else {
            exoPlayer?.stop()
            binding.detailPlayerView.visibility = View.GONE
            binding.detailPlayerWebView.visibility = View.VISIBLE

            val embedSrc = if (rawIframe.contains("src=\"") || rawIframe.contains("src='")) {
                val match = Regex("""src=["'](https?://[^"']+)["']""").find(rawIframe)
                match?.groupValues?.getOrNull(1) ?: videoUrl
            } else {
                videoUrl
            }

            if (embedSrc.isNotEmpty() && embedSrc.startsWith("http")) {
                binding.detailPlayerWebView.loadUrl(embedSrc)
            } else if (rawIframe.isNotEmpty()) {
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            * { margin:0 !important; padding:0 !important; box-sizing:border-box !important; background-color:#000000 !important; }
                            body, html { width:100% !important; height:100% !important; background:#000000 !important; overflow:hidden !important; }
                            iframe, video { width:100vw !important; height:100vh !important; border:none !important; display:block !important; }
                            .vjs-big-play-button, .ytp-cued-thumbnail-overlay, .play-wrapper { display:none !important; }
                        </style>
                    </head>
                    <body style="background-color:#000000; margin:0; padding:0;">
                        $rawIframe
                        <script>
                            window.addEventListener('DOMContentLoaded', function() {
                                var v = document.querySelector('video');
                                if (v) { v.play(); }
                            });
                        </script>
                    </body>
                    </html>
                """.trimIndent()
                binding.detailPlayerWebView.loadDataWithBaseURL("https://api.animekudesu.web.id", html, "text/html", "UTF-8", null)
            } else {
                showLoading(false)
                Toast.makeText(this, "Video sedang disiapkan atau coba server lain", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFullscreenState(landscape: Boolean) {
        isLandscape = landscape
        if (landscape) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
            binding.root.setBackgroundColor(Color.BLACK)
            binding.playerContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            binding.bstationTabBar.visibility = View.GONE
            binding.detailContentDivider.visibility = View.GONE
            binding.detailContentScroll.visibility = View.GONE
            binding.btnLandscapeServerToggle.visibility = View.VISIBLE
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_VISIBLE
            )
            binding.root.setBackgroundColor(Color.WHITE)
            val heightPx = (270 * resources.displayMetrics.density).toInt()
            binding.playerContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )
            binding.bstationTabBar.visibility = View.VISIBLE
            binding.detailContentDivider.visibility = View.VISIBLE
            binding.detailContentScroll.visibility = View.VISIBLE
            binding.btnLandscapeServerToggle.visibility = View.GONE
            binding.playerLandscapeServerBar.visibility = View.GONE
        }
    }

    private fun toggleFullscreen() {
        if (customView != null) {
            (binding.detailPlayerWebView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }

        if (isLandscape) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            applyFullscreenState(false)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            applyFullscreenState(true)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyFullscreenState(newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    override fun onBackPressed() {
        if (customView != null) {
            (binding.detailPlayerWebView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }
        if (isLandscape) {
            toggleFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        binding.detailPlayerWebView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.detailPlayerWebView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        binding.detailPlayerWebView.destroy()
    }
}
