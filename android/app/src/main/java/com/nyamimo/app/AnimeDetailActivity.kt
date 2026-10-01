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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.nyamimo.app.adapter.BstationEpisodeAdapter
import com.nyamimo.app.adapter.RecommendedAnimeAdapter
import com.nyamimo.app.adapter.ResolutionAdapter
import com.nyamimo.app.api.ApiClient
import com.nyamimo.app.api.JikanApiClient
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
    private var isImmersiveFullscreen = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private lateinit var bstationEpisodeAdapter: BstationEpisodeAdapter
    private lateinit var recommendedAnimeAdapter: RecommendedAnimeAdapter
    private lateinit var landscapeRecAdapter: RecommendedAnimeAdapter
    private lateinit var resolutionAdapter: ResolutionAdapter
    private lateinit var landscapeResolutionAdapter: ResolutionAdapter
    private var currentOptions: List<PlayerOption> = emptyList()
    private var currentActiveEpisode: EpisodeItem? = null

    private var isLiked = false
    private var isBookmarked = false
    private var baseLikeCount = 12500
    private var baseBookmarkCount = 45000
    private var baseViewCount = 150000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnimeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        slug = intent.getStringExtra("slug") ?: ""
        val previewTitle = ApiClient.cleanAnimeTitle(intent.getStringExtra("title") ?: "Nonton Anime")
        val previewScore = intent.getStringExtra("score") ?: "8.5"
        val previewTotalEps = intent.getStringExtra("episode") ?: ""

        // Set immediate preview information
        binding.tvDetailTitle.text = previewTitle
        binding.tvPlayerAnimeTitle.text = previewTitle
        binding.tvDetailScore.text = "Score $previewScore"
        binding.bannerRankRibbon.visibility = View.GONE
        if (previewTotalEps.isNotEmpty()) {
            binding.tvDetailTotalEps.text = if (previewTotalEps.all { it.isDigit() }) "$previewTotalEps Episode" else previewTotalEps
        }

        loadMalStats(previewTitle)

        binding.btnBackDetail.setOnClickListener {
            if (isImmersiveFullscreen || customView != null) {
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
        updateLayoutMode()
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

    private fun loadMalStats(animeTitle: String) {
        if (animeTitle.isEmpty() || animeTitle == "Nonton Anime") return
        JikanApiClient.getAnimeStats(
            animeTitle = animeTitle,
            onSuccess = { stats ->
                if (stats.members > 0) {
                    baseViewCount = stats.members
                    binding.tvDetailViews.text = "${JikanApiClient.formatCount(stats.members)} Ditonton"
                } else {
                    binding.tvDetailViews.text = "125K Ditonton"
                }

                if (stats.favorites > 0) {
                    baseLikeCount = stats.favorites
                    binding.tvDetailLikeCount.text = JikanApiClient.formatCount(stats.favorites)
                }

                if (stats.scoredBy > 0) {
                    baseBookmarkCount = stats.scoredBy
                    binding.tvDetailBookmarkCount.text = JikanApiClient.formatCount(stats.scoredBy)
                } else if (stats.members > 0) {
                    baseBookmarkCount = stats.members / 2
                    binding.tvDetailBookmarkCount.text = JikanApiClient.formatCount(baseBookmarkCount)
                }

                if (stats.score != "N/A" && stats.score.isNotEmpty()) {
                    binding.tvDetailScore.text = "Score ${stats.score}"
                }

                // Top 50 Badge logic (Only show if genuinely ranked #1 to #50)
                if (stats.rank in 1..50) {
                    binding.bannerRankRibbon.visibility = View.VISIBLE
                    binding.tvRankTitle.text = "Umum Top ${stats.rank} • Ranking Anime Nyamimo"
                } else {
                    binding.bannerRankRibbon.visibility = View.GONE
                }
            },
            onError = {
                binding.bannerRankRibbon.visibility = View.GONE
                if (binding.tvDetailViews.text.toString().contains("Memuat", ignoreCase = true)) {
                    binding.tvDetailViews.text = "250K Ditonton"
                }
            }
        )
    }

    private fun setupActionButtons() {
        binding.btnDetailLike.setOnClickListener {
            isLiked = !isLiked
            val count = if (isLiked) baseLikeCount + 1 else baseLikeCount
            if (isLiked) {
                binding.ivDetailLikeIcon.setColorFilter(Color.parseColor("#EF4444"))
                binding.tvDetailLikeCount.text = JikanApiClient.formatCount(count)
                binding.tvDetailLikeCount.setTextColor(Color.parseColor("#EF4444"))
                Toast.makeText(this, "Menyukai anime ini", Toast.LENGTH_SHORT).show()
            } else {
                binding.ivDetailLikeIcon.setColorFilter(Color.parseColor("#17171B"))
                binding.tvDetailLikeCount.text = JikanApiClient.formatCount(count)
                binding.tvDetailLikeCount.setTextColor(Color.parseColor("#17171B"))
            }
        }

        binding.btnDetailBookmark.setOnClickListener {
            isBookmarked = !isBookmarked
            val count = if (isBookmarked) baseBookmarkCount + 1 else baseBookmarkCount
            if (isBookmarked) {
                binding.ivDetailBookmarkIcon.setColorFilter(Color.parseColor("#FFCC00"))
                binding.tvDetailBookmarkCount.text = JikanApiClient.formatCount(count)
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
                binding.tvDetailBookmarkCount.text = JikanApiClient.formatCount(count)
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
        binding.rvDetailEpisodes.apply {
            layoutManager = LinearLayoutManager(this@AnimeDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            setItemViewCacheSize(15)
        }
        bstationEpisodeAdapter = BstationEpisodeAdapter(emptyList(), 0) { epItem, _ ->
            playEpisode(epItem)
        }
        binding.rvDetailEpisodes.adapter = bstationEpisodeAdapter
    }

    private fun setupRecommendations() {
        // Portrait Recommendation List
        binding.rvRecommendedAnime.apply {
            layoutManager = LinearLayoutManager(this@AnimeDetailActivity, LinearLayoutManager.VERTICAL, false)
            setHasFixedSize(true)
            setItemViewCacheSize(10)
        }
        recommendedAnimeAdapter = RecommendedAnimeAdapter(emptyList(), isGrid = false) { item ->
            navigateToDetail(item)
        }
        binding.rvRecommendedAnime.adapter = recommendedAnimeAdapter

        // Landscape 3-Column Grid Recommendations (iQIYI Style)
        binding.rvLandscapeRecommendations.apply {
            layoutManager = GridLayoutManager(this@AnimeDetailActivity, 3)
            setHasFixedSize(true)
            setItemViewCacheSize(12)
        }
        landscapeRecAdapter = RecommendedAnimeAdapter(emptyList(), isGrid = true) { item ->
            navigateToDetail(item)
        }
        binding.rvLandscapeRecommendations.adapter = landscapeRecAdapter
    }

    private fun navigateToDetail(item: AnimeItem) {
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

    private fun loadRecommendations() {
        ApiClient.getHome(object : ApiClient.Callback<HomeResponse> {
            override fun onSuccess(result: HomeResponse) {
                val list = mutableListOf<AnimeItem>()
                list.addAll(result.popular.take(12))
                if (list.size < 12) {
                    list.addAll(result.ongoing.take(12 - list.size))
                }
                recommendedAnimeAdapter.updateData(list)
                landscapeRecAdapter.updateData(list)
            }

            override fun onError(error: String) {
                // Silently fallback
            }
        })
    }

    private fun setupResolutionLists() {
        // 1. Portrait Resolution List
        binding.rvDetailResolutions.apply {
            layoutManager = LinearLayoutManager(this@AnimeDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            setItemViewCacheSize(8)
        }
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
        binding.rvLandscapeResolutions.apply {
            layoutManager = LinearLayoutManager(this@AnimeDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            setItemViewCacheSize(8)
        }
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
        binding.detailPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
                binding.playerContainer.addView(view, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                binding.detailPlayerWebView.visibility = View.GONE
                isImmersiveFullscreen = true
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                updateLayoutMode()
                binding.ivVideoWatermark.bringToFront()
                binding.ivVideoWatermark.visibility = View.VISIBLE
            }

            override fun onHideCustomView() {
                if (customView == null) return
                binding.playerContainer.removeView(customView)
                customView = null
                binding.detailPlayerWebView.visibility = View.VISIBLE
                isImmersiveFullscreen = false
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                updateLayoutMode()
                binding.ivVideoWatermark.bringToFront()
                binding.ivVideoWatermark.visibility = View.VISIBLE
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
                loadMalStats(cleanTitle)

                val rawEpisodes = result.episodes
                if (rawEpisodes.isNotEmpty()) {
                    // Sort episodes ASCENDING (Episode 1, 2, 3 ... onwards)
                    val episodes = rawEpisodes.sortedWith(compareBy { item ->
                        val num = item.number.toIntOrNull() ?: item.episode.toIntOrNull()
                        val match = Regex("""\b(?:Episode|Eps|Ep)\s*(\d+)""", RegexOption.IGNORE_CASE).find(item.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        num ?: match ?: 9999
                    })

                    val targetEpNumber = intent.getStringExtra("target_episode") ?: ""
                    var targetIndex = 0
                    if (targetEpNumber.isNotEmpty()) {
                        val foundIndex = episodes.indexOfFirst {
                            val epNum = if (it.number.isNotEmpty()) it.number else it.episode
                            epNum == targetEpNumber || it.title.contains("Episode $targetEpNumber", ignoreCase = true)
                        }
                        if (foundIndex >= 0) {
                            targetIndex = foundIndex
                        }
                    }

                    val targetEp = episodes[targetIndex]

                    bstationEpisodeAdapter.updateData(episodes, targetIndex)
                    binding.rvDetailEpisodes.scrollToPosition(targetIndex)

                    playEpisode(targetEp)
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
            binding.ivVideoWatermark.bringToFront()
            binding.ivVideoWatermark.visibility = View.VISIBLE

            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
        } else {
            exoPlayer?.stop()
            binding.detailPlayerView.visibility = View.GONE
            binding.detailPlayerWebView.visibility = View.VISIBLE
            binding.ivVideoWatermark.bringToFront()
            binding.ivVideoWatermark.visibility = View.VISIBLE

            val embedSrc = if (rawIframe.contains("src=\"") || rawIframe.contains("src='")) {
                val match = Regex("""src=["'](https?://[^"']+)["']""").find(rawIframe)
                match?.groupValues?.getOrNull(1) ?: videoUrl
            } else {
                videoUrl
            }

            if (embedSrc.isNotEmpty() || rawIframe.isNotEmpty()) {
                val iframeCode = if (rawIframe.isNotEmpty()) {
                    rawIframe
                } else {
                    "<iframe src=\"$embedSrc\" allowfullscreen=\"true\" allow=\"autoplay; fullscreen\"></iframe>"
                }

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            * { margin:0 !important; padding:0 !important; box-sizing:border-box !important; background-color:#000000 !important; }
                            body, html { width:100% !important; height:100% !important; background:#000000 !important; overflow:hidden !important; }
                            iframe, video { 
                                width: 100vw !important; 
                                height: 100vh !important; 
                                border: none !important; 
                                display: block !important; 
                            }
                            .vjs-big-play-button, .ytp-cued-thumbnail-overlay, .play-wrapper { display:none !important; }
                        </style>
                    </head>
                    <body style="background-color:#000000; margin:0; padding:0;">
                        $iframeCode
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

    private fun updateLayoutMode() {
        val isOrientationLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (isImmersiveFullscreen) {
            // 100% IMMERSIVE FULLSCREEN MODE
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
            binding.rootAnimeDetail.setBackgroundColor(Color.BLACK)
            binding.mainContentRow.orientation = LinearLayout.HORIZONTAL

            binding.leftColumnLayout.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            binding.playerContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            binding.detailContentScroll.visibility = View.GONE
            binding.rightColumnLayout.visibility = View.GONE
            binding.columnDivider.visibility = View.GONE
            binding.bstationTabBar.visibility = View.GONE
            binding.detailContentDivider.visibility = View.GONE
            binding.playerTopControls.visibility = View.GONE
            binding.btnLandscapeServerToggle.visibility = View.GONE
            binding.playerLandscapeServerBar.visibility = View.GONE
        } else if (isOrientationLandscape) {
            // 2-COLUMN LANDSCAPE TABLET MODE
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_VISIBLE
            )
            binding.rootAnimeDetail.setBackgroundColor(Color.WHITE)
            binding.mainContentRow.orientation = LinearLayout.HORIZONTAL

            // Left Column (Player + Episode & Info Scroll): 62% width
            binding.leftColumnLayout.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                62f
            )

            // Exactly 16:9 aspect ratio of left column width (eliminates all left/right black pillarbox bars)
            val leftColumnWidthPx = resources.displayMetrics.widthPixels * 0.62f
            val playerHeightPx = (leftColumnWidthPx * 9f / 16f).toInt()
            binding.playerContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                playerHeightPx
            )

            binding.detailContentScroll.visibility = View.VISIBLE
            binding.portraitRecommendationsContainer.visibility = View.GONE

            // Right Column (3-Column Rekomendasi Grid): 38% width
            binding.rightColumnLayout.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                38f
            )
            binding.rightColumnLayout.visibility = View.VISIBLE
            binding.columnDivider.visibility = View.VISIBLE

            binding.bstationTabBar.visibility = View.GONE
            binding.detailContentDivider.visibility = View.GONE
            binding.playerTopControls.visibility = View.VISIBLE
            binding.btnLandscapeServerToggle.visibility = View.GONE
            binding.playerLandscapeServerBar.visibility = View.GONE
        } else {
            // PORTRAIT MODE
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_VISIBLE
            )
            binding.rootAnimeDetail.setBackgroundColor(Color.WHITE)
            binding.mainContentRow.orientation = LinearLayout.VERTICAL

            binding.leftColumnLayout.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val screenWidthPx = resources.displayMetrics.widthPixels
            val playerHeightPx = (screenWidthPx * 9f / 16f).toInt()
            binding.playerContainer.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                playerHeightPx
            )

            binding.detailContentScroll.visibility = View.VISIBLE
            binding.portraitRecommendationsContainer.visibility = View.VISIBLE
            binding.rightColumnLayout.visibility = View.GONE
            binding.columnDivider.visibility = View.GONE

            binding.bstationTabBar.visibility = View.VISIBLE
            binding.detailContentDivider.visibility = View.VISIBLE
            binding.playerTopControls.visibility = View.VISIBLE
            binding.btnLandscapeServerToggle.visibility = View.GONE
            binding.playerLandscapeServerBar.visibility = View.GONE
        }

        binding.ivVideoWatermark.bringToFront()
        binding.ivVideoWatermark.visibility = View.VISIBLE
    }

    private fun toggleFullscreen() {
        if (customView != null) {
            (binding.detailPlayerWebView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }

        isImmersiveFullscreen = !isImmersiveFullscreen
        if (isImmersiveFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        updateLayoutMode()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLayoutMode()
    }

    override fun onBackPressed() {
        if (customView != null) {
            (binding.detailPlayerWebView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            return
        }
        if (isImmersiveFullscreen) {
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
