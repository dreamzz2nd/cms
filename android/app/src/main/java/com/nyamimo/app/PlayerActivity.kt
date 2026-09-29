package com.nyamimo.app

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.nyamimo.app.adapter.ResolutionAdapter
import com.nyamimo.app.api.ApiClient
import com.nyamimo.app.databinding.ActivityPlayerBinding
import com.nyamimo.app.model.AnimeItem
import com.nyamimo.app.model.EpisodeDataResponse
import com.nyamimo.app.model.PlayerOption
import com.nyamimo.app.util.SessionManager

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var isZoomMode = false
    private lateinit var resolutionAdapter: ResolutionAdapter
    private var availableOptions: List<PlayerOption> = emptyList()

    private val playbackSpeeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIndex = 1 // default 1.0x

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lock to landscape sensor for cinema video streaming
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val detailEps = intent.getStringExtra("detail_eps") ?: ""
        val title = ApiClient.cleanAnimeTitle(intent.getStringExtra("title") ?: "Nonton Anime")
        val ep = intent.getStringExtra("ep") ?: "1"
        val slug = intent.getStringExtra("slug") ?: ""
        val img = intent.getStringExtra("img") ?: ""
        val synopsis = intent.getStringExtra("synopsis") ?: "Terakhir ditonton episode $ep"

        binding.tvPlayerTitle.text = title
        binding.tvPlayerEpisode.text = "Episode $ep"

        // Save to watch history automatically
        SessionManager.addWatchHistory(
            this,
            AnimeItem(
                title = title,
                slug = slug,
                img = img,
                episode = ep,
                score = "8.8",
                type = "Anime",
                status = "Watching",
                synopsis = synopsis
            )
        )

        binding.btnBackPlayer.setOnClickListener {
            finish()
        }

        binding.btnAspect.setOnClickListener {
            toggleAspectRatio()
        }

        binding.btnSpeed.setOnClickListener {
            cyclePlaybackSpeed()
        }

        binding.btnServerToggle.setOnClickListener {
            if (binding.playerBottomOverlay.visibility == View.VISIBLE) {
                binding.playerBottomOverlay.visibility = View.GONE
            } else {
                binding.playerBottomOverlay.visibility = View.VISIBLE
            }
        }

        setupResolutionList(title, ep)
        initWebView()
        initExoPlayer()
        loadEpisodeStream(detailEps, title, ep)
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            binding.ivPlayerLoadingGif.visibility = View.VISIBLE
            Glide.with(this).asGif().load(R.raw.loading_cat).into(binding.ivPlayerLoadingGif)
        } else {
            binding.ivPlayerLoadingGif.visibility = View.GONE
        }
    }

    private fun setupResolutionList(title: String, ep: String) {
        binding.rvPlayerResolutions.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        resolutionAdapter = ResolutionAdapter(emptyList(), 0, isDarkMode = true) { option, index ->
            binding.playerBottomOverlay.visibility = View.GONE
            switchResolution(option, index, title, ep)
        }
        binding.rvPlayerResolutions.adapter = resolutionAdapter
    }

    private fun initExoPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 8000, 500, 1000)
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
                        Toast.makeText(this@PlayerActivity, "Player Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        binding.playerView.player = exoPlayer
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        binding.playerWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(binding.playerWebView, true)
        } catch (e: Exception) {
            // Ignore
        }

        binding.playerWebView.settings.apply {
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

        binding.playerWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                showLoading(false)
            }
        }
        binding.playerWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 40) {
                    showLoading(false)
                }
            }
        }
    }

    private fun loadEpisodeStream(detailEps: String, title: String, ep: String) {
        showLoading(true)

        ApiClient.getEpisodeData(detailEps, title, ep, object : ApiClient.Callback<EpisodeDataResponse> {
            override fun onSuccess(result: EpisodeDataResponse) {
                availableOptions = result.videos
                if (availableOptions.isNotEmpty()) {
                    val activeIndex = availableOptions.indexOfFirst { it.title == result.activeServerTitle }.coerceAtLeast(0)
                    resolutionAdapter.updateData(availableOptions, activeIndex)
                }

                playStream(result)
            }

            override fun onError(error: String) {
                showLoading(false)
                Toast.makeText(this@PlayerActivity, error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun switchResolution(option: PlayerOption, index: Int, title: String, ep: String) {
        showLoading(true)
        Toast.makeText(this, "Mengganti ke ${option.title}...", Toast.LENGTH_SHORT).show()

        ApiClient.resolveVideoOption(option, ep, title, availableOptions, object : ApiClient.Callback<EpisodeDataResponse> {
            override fun onSuccess(result: EpisodeDataResponse) {
                val activeIndex = availableOptions.indexOfFirst { it.title == option.title }.coerceAtLeast(0)
                resolutionAdapter.updateData(availableOptions, activeIndex)
                playStream(result)
            }

            override fun onError(error: String) {
                showLoading(false)
                Toast.makeText(this@PlayerActivity, error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun playStream(result: EpisodeDataResponse) {
        val videoUrl = result.videoURL
        val rawIframe = result.rawIframe
        val isDirect = result.isDirectVideo || videoUrl.endsWith(".mp4") || videoUrl.endsWith(".m3u8")

        if (isDirect && videoUrl.isNotEmpty()) {
            binding.playerWebView.visibility = View.GONE
            binding.playerView.visibility = View.VISIBLE

            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
        } else {
            exoPlayer?.stop()
            binding.playerView.visibility = View.GONE
            binding.playerWebView.visibility = View.VISIBLE

            val embedSrc = if (rawIframe.contains("src=\"") || rawIframe.contains("src='")) {
                val match = Regex("""src=["'](https?://[^"']+)["']""").find(rawIframe)
                match?.groupValues?.getOrNull(1) ?: videoUrl
            } else {
                videoUrl
            }

            if (embedSrc.isNotEmpty() && embedSrc.startsWith("http")) {
                binding.playerWebView.loadUrl(embedSrc)
            } else if (rawIframe.isNotEmpty()) {
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            * { margin:0; padding:0; box-sizing:border-box; }
                            body, html { width:100%; height:100%; background:#000; overflow:hidden; }
                            iframe, video { width:100vw; height:100vh; border:none; display:block; }
                        </style>
                    </head>
                    <body>
                        $rawIframe
                    </body>
                    </html>
                """.trimIndent()
                binding.playerWebView.loadDataWithBaseURL("https://api.animekudesu.web.id", html, "text/html", "UTF-8", null)
            } else {
                showLoading(false)
                Toast.makeText(this@PlayerActivity, "Video sedang disiapkan atau coba server lain", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cyclePlaybackSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.size
        val speed = playbackSpeeds[currentSpeedIndex]
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        binding.btnSpeed.text = "${speed}x"
        Toast.makeText(this, "Kecepatan: ${speed}x", Toast.LENGTH_SHORT).show()
    }

    private fun toggleAspectRatio() {
        isZoomMode = !isZoomMode
        binding.playerView.resizeMode = if (isZoomMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val modeText = if (isZoomMode) "Penuh Layar (Zoom)" else "Asli (Fit)"
        Toast.makeText(this, "Rasio: $modeText", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        binding.playerWebView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.playerWebView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
        binding.playerWebView.destroy()
    }
}
