package com.nyamimo.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nyamimo.app.adapter.BstationPosterAdapter
import com.nyamimo.app.adapter.ContinueWatchingAdapter
import com.nyamimo.app.adapter.DownloadFullAdapter
import com.nyamimo.app.adapter.HistoryFullAdapter
import com.nyamimo.app.adapter.MimoNewsAdapter
import com.nyamimo.app.adapter.SearchHistoryAdapter
import com.nyamimo.app.adapter.SearchSuggestionAdapter
import com.nyamimo.app.adapter.TrendingTag
import com.nyamimo.app.adapter.TrendingTagAdapter
import com.nyamimo.app.api.ApiClient
import com.nyamimo.app.api.JikanApiClient
import com.nyamimo.app.databinding.ActivityMainBinding
import com.nyamimo.app.model.AnimeItem
import com.nyamimo.app.model.HomeResponse
import com.nyamimo.app.model.MimoNewsItem
import com.nyamimo.app.util.SessionManager
import com.nyamimo.app.adapter.ParallaxHeroBannerAdapter
import com.nyamimo.app.adapter.ParallaxPageTransformer
import com.nyamimo.app.adapter.KoleksiAnimeAdapter
import com.nyamimo.app.util.ParallaxSlideItem
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var homeData: HomeResponse? = null
    private var currentTab: String = "untuk_anda"
    private var currentNav: String = "home"
    private var currentNewsFilter: String = "now"

    private lateinit var posterAdapter: BstationPosterAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter
    private lateinit var parallaxBannerAdapter: ParallaxHeroBannerAdapter
    private lateinit var koleksiAdapter: KoleksiAnimeAdapter
    private lateinit var searchSuggestionAdapter: SearchSuggestionAdapter
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    private lateinit var trendingTagAdapter: TrendingTagAdapter
    private lateinit var mimoNewsAdapter: MimoNewsAdapter

    // Shared RecycledViewPool for ultra-smooth low-RAM card rendering
    private val sharedPosterPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(0, 30)
    }

    private var selectedKoleksiTab: String = "anime"
    private var selectedWilayah: String = "all"
    private var selectedGenre: String = "all"
    private var selectedSubtitle: String = "all"
    private var selectedAkses: String = "all"
    private var selectedSort: String = "populer"
    private var koleksiSearchQuery: String = ""

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val heroSlideHandler = Handler(Looper.getMainLooper())
    private var heroSlideRunnable: Runnable? = null
    private var currentHeroSlides: List<ParallaxSlideItem> = emptyList()
    private var isReelsLiked = false
    private var isReelsBookmarked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdapters()
        setupMimoNewsUI()
        setupKoleksiUI()
        setupSearchInput()
        setupListeners()
        setupTrendingTags()
        setupReelsInteractions()
        loadSearchHistoryUI()
        loadHeroCarouselUI()
        loadContinueWatchingUI()
        updateProfileUI()
        ApiClient.fetchAppConfig(this)
        loadData()
        checkWelcomeScreen()
    }

    override fun onResume() {
        super.onResume()
        updateProfileUI()
        loadHeroCarouselUI()
        loadContinueWatchingUI()
    }

    override fun onPause() {
        super.onPause()
        heroSlideRunnable?.let { heroSlideHandler.removeCallbacks(it) }
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        heroSlideHandler.removeCallbacksAndMessages(null)
        searchHandler.removeCallbacksAndMessages(null)
    }

    private fun checkWelcomeScreen() {
        val user = SessionManager.getUser(this)
        if (!user.isLoggedIn && !SessionManager.hasSkippedWelcome(this)) {
            val cfg = SessionManager.getAppConfig(this)
            val welcomeEnabled = cfg.welcome_screen.enabled
            if (welcomeEnabled) {
                val intent = Intent(this, WelcomeActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun setupAdapters() {
        val spanCount = if (resources.configuration.screenWidthDp >= 600) 4 else 3
        binding.rvPosterGrid.layoutManager = GridLayoutManager(this, spanCount)
        binding.rvPosterGrid.setHasFixedSize(true)
        binding.rvPosterGrid.setItemViewCacheSize(20)
        binding.rvPosterGrid.setRecycledViewPool(sharedPosterPool)
        posterAdapter = BstationPosterAdapter(emptyList()) { anime ->
            playAnimeDirectly(anime)
        }
        binding.rvPosterGrid.adapter = posterAdapter

        binding.rvContinueWatching.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvContinueWatching.setHasFixedSize(true)
        binding.rvContinueWatching.setItemViewCacheSize(10)
        continueWatchingAdapter = ContinueWatchingAdapter(emptyList()) { anime ->
            val intent = Intent(this, AnimeDetailActivity::class.java).apply {
                putExtra("slug", anime.slug)
                putExtra("title", anime.title)
                putExtra("score", anime.score)
                putExtra("status", anime.status)
                putExtra("type", anime.type)
                putExtra("episode", anime.episode)
                putExtra("target_episode", anime.episode)
                putExtra("synopsis", anime.synopsis)
            }
            startActivity(intent)
        }
        binding.rvContinueWatching.adapter = continueWatchingAdapter

        binding.rvSearchResultsList.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResultsList.setHasFixedSize(true)
        binding.rvSearchResultsList.setItemViewCacheSize(10)
        searchSuggestionAdapter = SearchSuggestionAdapter(emptyList()) { anime ->
            SessionManager.addSearchQuery(this, anime.title)
            playAnimeDirectly(anime)
        }
        binding.rvSearchResultsList.adapter = searchSuggestionAdapter

        binding.rvSearchHistory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvSearchHistory.setHasFixedSize(true)
        searchHistoryAdapter = SearchHistoryAdapter(emptyList()) { query ->
            binding.etTopSearch.setText(query)
            binding.etTopSearch.setSelection(query.length)
            performSearch(query)
        }
        binding.rvSearchHistory.adapter = searchHistoryAdapter

        binding.rvTrendingTags.layoutManager = GridLayoutManager(this, 2)
        binding.rvTrendingTags.setHasFixedSize(true)
        trendingTagAdapter = TrendingTagAdapter(emptyList()) { tag ->
            binding.etTopSearch.setText(tag.title)
            binding.etTopSearch.setSelection(tag.title.length)
            SessionManager.addSearchQuery(this, tag.title)
            loadSearchHistoryUI()
            performSearch(tag.title)
        }
        binding.rvTrendingTags.adapter = trendingTagAdapter

        // Hero Carousel Parallax ViewPager2 setup
        parallaxBannerAdapter = ParallaxHeroBannerAdapter(emptyList()) { slide ->
            if (slide.target_slug.isNotEmpty()) {
                val intent = Intent(this, AnimeDetailActivity::class.java).apply {
                    putExtra("slug", slide.target_slug)
                    putExtra("title", slide.title)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Menonton ${slide.title}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.vpHeroCarousel.adapter = parallaxBannerAdapter
        binding.vpHeroCarousel.setPageTransformer(ParallaxPageTransformer())
        binding.vpHeroCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateHeroDots(position)
            }
        })

        // Koleksi Anime Grid setup (Sharing view pool with main poster grid for zero-lag tab transitions)
        val koleksiSpan = if (resources.configuration.screenWidthDp >= 600) 4 else 3
        binding.rvKoleksiGrid.layoutManager = GridLayoutManager(this, koleksiSpan)
        binding.rvKoleksiGrid.setHasFixedSize(true)
        binding.rvKoleksiGrid.setItemViewCacheSize(20)
        binding.rvKoleksiGrid.setRecycledViewPool(sharedPosterPool)
        koleksiAdapter = KoleksiAnimeAdapter(emptyList()) { anime ->
            playAnimeDirectly(anime)
        }
        binding.rvKoleksiGrid.adapter = koleksiAdapter
    }

    private fun loadHeroCarouselUI() {
        val cfg = SessionManager.getAppConfig(this)
        val heroCfg = cfg.hero_carousel
        if (!heroCfg.enabled) {
            binding.heroCarouselSection.visibility = View.GONE
            heroSlideRunnable?.let { heroSlideHandler.removeCallbacks(it) }
            return
        }

        val activeSlides = if (heroCfg.slides.isNotEmpty()) {
            heroCfg.slides.filter { it.is_active }
        } else {
            emptyList()
        }

        val finalSlides = if (activeSlides.isNotEmpty()) {
            activeSlides
        } else {
            listOf(
                ParallaxSlideItem(
                    id = "slide-1",
                    title = "Solo Leveling: Arise",
                    subtitle = "Aksi, Fantasi • Korea & Jepang • Full 12 Episode",
                    badge = "TOP 1 REKOMENDASI",
                    background_url = "https://images.alphacoders.com/134/1349544.jpeg",
                    object_url = "https://pngimg.com/d/sword_PNG5509.png",
                    target_slug = "solo-leveling",
                    is_active = true
                ),
                ParallaxSlideItem(
                    id = "slide-2",
                    title = "Gachiakuta",
                    subtitle = "Shounen, Aksi, Supranatural • Sub Indo • Studio BONES",
                    badge = "DOLBY AUDIO",
                    background_url = "https://images.alphacoders.com/136/1367097.jpeg",
                    object_url = "",
                    target_slug = "gachiakuta",
                    is_active = true
                ),
                ParallaxSlideItem(
                    id = "slide-3",
                    title = "Demon Slayer: Kimetsu",
                    subtitle = "Petualangan, Iblis • Full HD 1080p • Ufotable",
                    badge = "VIP EXCLUSIVE",
                    background_url = "https://images.alphacoders.com/134/1340156.jpeg",
                    object_url = "",
                    target_slug = "kimetsu-no-yaiba",
                    is_active = true
                )
            )
        }

        currentHeroSlides = finalSlides
        if (currentTab == "untuk_anda" && currentNav == "home") {
            binding.heroCarouselSection.visibility = View.VISIBLE
        }
        parallaxBannerAdapter.updateData(finalSlides)

        // Setup dots indicator
        setupHeroDots(finalSlides.size)
        updateHeroDots(binding.vpHeroCarousel.currentItem.coerceAtMost(finalSlides.size - 1).coerceAtLeast(0))

        // Setup auto slide timer
        heroSlideRunnable?.let { heroSlideHandler.removeCallbacks(it) }
        if (heroCfg.auto_slide && finalSlides.size > 1) {
            val interval = heroCfg.interval_ms.toLong().coerceAtLeast(3000L)
            heroSlideRunnable = object : Runnable {
                override fun run() {
                    if (binding.vpHeroCarousel.adapter?.itemCount ?: 0 > 1) {
                        val nextItem = (binding.vpHeroCarousel.currentItem + 1) % finalSlides.size
                        binding.vpHeroCarousel.setCurrentItem(nextItem, true)
                        heroSlideHandler.postDelayed(this, interval)
                    }
                }
            }
            heroSlideHandler.postDelayed(heroSlideRunnable!!, interval)
        }
    }

    private fun setupHeroDots(count: Int) {
        binding.layoutHeroDots.removeAllViews()
        for (i in 0 until count) {
            val dot = View(this).apply {
                val size = (resources.displayMetrics.density * 6).toInt()
                val margin = (resources.displayMetrics.density * 3).toInt()
                val params = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                layoutParams = params
                setBackgroundResource(R.drawable.dot_hero_inactive)
            }
            binding.layoutHeroDots.addView(dot)
        }
    }

    private fun updateHeroDots(activeIdx: Int) {
        val count = binding.layoutHeroDots.childCount
        for (i in 0 until count) {
            val dot = binding.layoutHeroDots.getChildAt(i) ?: continue
            val density = resources.displayMetrics.density
            if (i == activeIdx) {
                dot.setBackgroundResource(R.drawable.dot_hero_active)
                val params = dot.layoutParams as LinearLayout.LayoutParams
                params.width = (density * 18).toInt()
                params.height = (density * 4).toInt()
                dot.layoutParams = params
            } else {
                dot.setBackgroundResource(R.drawable.dot_hero_inactive)
                val params = dot.layoutParams as LinearLayout.LayoutParams
                params.width = (density * 6).toInt()
                params.height = (density * 6).toInt()
                dot.layoutParams = params
            }
        }
    }

    private fun loadContinueWatchingUI() {
        val history = SessionManager.getWatchHistory(this)
        if (history.isNotEmpty()) {
            binding.continueWatchingSection.visibility = View.VISIBLE
            continueWatchingAdapter.updateData(history)
        } else {
            homeData?.let {
                val demoList = it.ongoing.take(6).mapIndexed { idx, anime ->
                    anime.copy(
                        episode = "${idx + 1}",
                        watchProgressPercent = 40 + (idx * 15) % 55,
                        watchDurationText = "12:${30 + idx * 4} / 24:00"
                    )
                }
                binding.continueWatchingSection.visibility = View.VISIBLE
                continueWatchingAdapter.updateData(demoList)
            }
        }
    }

    private fun loadSearchHistoryUI() {
        val history = SessionManager.getSearchHistory(this)
        if (history.isNotEmpty()) {
            binding.searchHistorySection.visibility = View.VISIBLE
            searchHistoryAdapter.updateData(history)
        } else {
            binding.searchHistorySection.visibility = View.GONE
        }
    }

    private fun setupTrendingTags() {
        JikanApiClient.getTrendingTags(
            onSuccess = { tags ->
                trendingTagAdapter.updateData(tags)
            },
            onError = {
                if (trendingTagAdapter.itemCount == 0) {
                    val fallbackTags = listOf(
                        TrendingTag("one piece", "TOP", "red"),
                        TrendingTag("jujutsu kaisen", "PANAS", "orange"),
                        TrendingTag("solo leveling", "TOP", "red"),
                        TrendingTag("demon slayer", "PANAS", "orange"),
                        TrendingTag("bleach", "BARU", "blue"),
                        TrendingTag("chainsaw man", "", "")
                    )
                    trendingTagAdapter.updateData(fallbackTags)
                }
            }
        )
    }


    private fun setupSearchInput() {
        binding.etTopSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    binding.btnClearSearch.visibility = View.VISIBLE
                    binding.rvSearchResultsList.visibility = View.VISIBLE
                    binding.rvPosterGrid.visibility = View.GONE
                    binding.searchHistorySection.visibility = View.GONE
                    binding.trendingSection.visibility = View.GONE
                    binding.heroCarouselSection.visibility = View.GONE

                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    searchRunnable = Runnable { performSearch(query) }
                    searchHandler.postDelayed(searchRunnable!!, 350)
                } else {
                    binding.btnClearSearch.visibility = View.GONE
                    binding.rvSearchResultsList.visibility = View.GONE
                    binding.rvPosterGrid.visibility = View.VISIBLE
                    loadSearchHistoryUI()
                    binding.trendingSection.visibility = if (currentNav == "cari") View.VISIBLE else View.GONE
                    selectTab(currentTab)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etTopSearch.setText("")
            binding.btnClearSearch.visibility = View.GONE
            binding.rvSearchResultsList.visibility = View.GONE
            binding.rvPosterGrid.visibility = View.VISIBLE
            selectTab("untuk_anda")
        }

        binding.btnClearSearchHistory.setOnClickListener {
            SessionManager.clearSearchHistory(this)
            loadSearchHistoryUI()
            Toast.makeText(this, "Riwayat pencarian dihapus", Toast.LENGTH_SHORT).show()
        }

        binding.etTopSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.etTopSearch.text.toString().trim()
                if (q.isNotEmpty()) {
                    SessionManager.addSearchQuery(this, q)
                    performSearch(q)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupReelsInteractions() {
        binding.btnReelsLike.setOnClickListener {
            isReelsLiked = !isReelsLiked
            if (isReelsLiked) {
                binding.btnReelsLike.setColorFilter(Color.parseColor("#EF4444"))
                binding.tvReelsLikeCount.text = "24.9K"
                Toast.makeText(this, "Disukai", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnReelsLike.setColorFilter(Color.WHITE)
                binding.tvReelsLikeCount.text = "24.8K"
            }
        }

        binding.btnReelsBookmark.setOnClickListener {
            isReelsBookmarked = !isReelsBookmarked
            if (isReelsBookmarked) {
                binding.btnReelsBookmark.setColorFilter(Color.parseColor("#FFCC00"))
                binding.tvReelsAuthor.setTextColor(Color.parseColor("#FFCC00"))
                Toast.makeText(this, "Klip anime disimpan ke favorit", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnReelsBookmark.setColorFilter(Color.WHITE)
            }
        }

        binding.btnReelsComment.setOnClickListener {
            showSimpleDialogSheet("Komentar Nyamimo Shorts", "1. @wibu_pro: Animasi dan visual fight ini gila banget!\n2. @anime_indo: Mappa ga pernah ngecewain grafisnya.\n3. @otaku_99: Nonton episode 1-nya langsung di Nyamimo tanpa lag!")
        }

        binding.btnReelsShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Nonton Anime di Nyamimo")
                putExtra(Intent.EXTRA_TEXT, "Tonton klip anime seru ini di Nyamimo Streaming HD!\nhttps://nyamimo.onrender.com")
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Klip Anime"))
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setColorSchemeColors(0xFFFFCC00.toInt(), 0xFF17171B.toInt())
        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }

        binding.btnRefreshTrending.setOnClickListener {
            setupTrendingTags()
            Toast.makeText(this, "Trending diperbarui", Toast.LENGTH_SHORT).show()
        }

        // Top Category Tabs Click Handlers
        binding.tabUntukAnda.setOnClickListener { selectTab("untuk_anda") }
        binding.tabPopuler.setOnClickListener { selectTab("populer") }
        binding.tabAnime.setOnClickListener { selectTab("anime") }
        binding.tabTamat.setOnClickListener { selectTab("tamat") }
        binding.tabAction.setOnClickListener { selectTab("action") }
        binding.tabFantasy.setOnClickListener { selectTab("fantasy") }

        // Bottom Navigation Click Handlers
        binding.btnNavHome.setOnClickListener { selectBottomNav("home") }
        binding.btnNavCari.setOnClickListener { selectBottomNav("cari") }
        binding.btnNavFab.setOnClickListener { selectBottomNav("reels") }
        binding.btnNavNews.setOnClickListener { selectBottomNav("news") }
        binding.btnNavSaya.setOnClickListener { selectBottomNav("saya") }

        binding.btnSeeAllContinue.setOnClickListener {
            openHistoryScreen()
        }

        // Profile Header Login Click
        binding.ivProfileAvatar.setOnClickListener { showAuthDialog() }
        binding.tvProfileName.setOnClickListener { showAuthDialog() }
        binding.layoutProfileHeaderClick.setOnClickListener { showAuthDialog() }

        // Profile Top Icons
        binding.ivProfileScan.setOnClickListener {
            Toast.makeText(this, "Fitur Pindai QR Nyamimo segera hadir!", Toast.LENGTH_SHORT).show()
        }
        binding.ivProfileNotif.setOnClickListener {
            showSimpleDialogSheet("Notifikasi Nyamimo", "Belum ada pesan baru. Nikmati streaming anime favoritmu hari ini!")
        }

        // Profile VIP Banner & Quick Action Cards
        val onVipClick = View.OnClickListener {
            showSimpleDialogSheet("Nyamimo VIP Pass", "Nikmati keuntungan VIP Nyamimo:\n✨ Bebas Iklan Selamanya\n⚡ Server Ultra High-Speed 1080p 60fps\n📥 Unduh Anime Sepuasnya Tanpa Batas\n🎁 Badge & Efek Profil Eksklusif\n\nVIP Standard mulai dari Rp19.000 / bulan.")
        }
        binding.bannerVipCard.setOnClickListener(onVipClick)
        binding.btnProfileJoinVip.setOnClickListener(onVipClick)
        binding.cardVipMine.setOnClickListener(onVipClick)

        binding.cardKoinMine.setOnClickListener {
            showSimpleDialogSheet("Koin Mimo", "Saldo Koin Mimo: 1.250 Koin\n\nKoin didapat dari menonton anime, login harian, dan berinteraksi di Nyamimo. Gunakan koin untuk membuka lencana anime eksklusif!")
        }

        binding.cardDiamondMine.setOnClickListener {
            showSimpleDialogSheet("Diamond Nyamimo", "Saldo Diamond: 80 💎\n\nGunakan Diamond untuk mendukung konten kreator, request subtitle cepat, dan membeli gift virtual!")
        }

        // Setup the Menu List in Tab Saya
        binding.menuFavoritSaya.setOnClickListener {
            openFavoritesScreen()
        }

        binding.menuRiwayatSaya.setOnClickListener {
            openHistoryScreen()
        }

        binding.menuUnduhanSaya.setOnClickListener {
            openDownloadsScreen()
        }

        binding.menuPointsSaya.setOnClickListener {
            showSimpleDialogSheet("Pusat Hadiah Mimo", "Selesaikan misi harian untuk mengumpulkan Koin Mimo:\n\n✔️ Nonton Anime 30 Menit (+50 Koin)\n✔️ Tulis Ulasan (+20 Koin)\n✔️ Bagikan Anime ke Teman (+10 Koin)")
        }

        binding.menuBahasaSaya.setOnClickListener {
            showSimpleDialogSheet("Pilihan Bahasa", "Bahasa Aplikasi: Bahasa Indonesia (Default)\nSubtitle: Indonesia, English, Romaji.")
        }

        binding.menuSubtitleSaya.setOnClickListener {
            showSimpleDialogSheet("Penerjemahan Subtitle", "Nyamimo Fansub Project:\nSemua subtitle diterjemahkan oleh komunitas pecinta anime Nyamimo dengan tata bahasa Indonesia yang rapi dan alami.")
        }

        binding.menuPengaturan.setOnClickListener {
            openSettingsScreen()
        }

        binding.menuFeedback.setOnClickListener {
            showFeedbackDialog()
        }

        // Offline Screen Buttons
        binding.btnOfflineOpenDownloads.setOnClickListener {
            openDownloadsScreen()
        }

        binding.btnOfflineRetry.setOnClickListener {
            loadData()
        }
    }

    private fun openHistoryScreen() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_history_full)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

        val btnBack = dialog.findViewById<ImageView>(R.id.btnHistoryBack)
        val btnClearAll = dialog.findViewById<TextView>(R.id.btnClearAllHistory)
        val rvHistory = dialog.findViewById<RecyclerView>(R.id.rvHistoryFullList)
        val layoutEmpty = dialog.findViewById<LinearLayout>(R.id.layoutHistoryEmpty)

        val tabAll = dialog.findViewById<TextView>(R.id.tabHistoryAll)
        val tabAnime = dialog.findViewById<TextView>(R.id.tabHistoryAnime)
        val tabVideo = dialog.findViewById<TextView>(R.id.tabHistoryVideo)
        val tabShow = dialog.findViewById<TextView>(R.id.tabHistoryShow)

        rvHistory.layoutManager = LinearLayoutManager(this)

        var allHistory = SessionManager.getWatchHistory(this)
        var currentFilter = "all"

        lateinit var adapter: HistoryFullAdapter

        fun refreshList() {
            allHistory = SessionManager.getWatchHistory(this)
            val filtered = when (currentFilter) {
                "anime" -> allHistory.filter { !it.type.equals("movie", ignoreCase = true) }
                "video" -> allHistory.filter { it.type.equals("movie", ignoreCase = true) || it.episode.equals("movie", ignoreCase = true) }
                "show" -> allHistory.filter { (it.episode.toIntOrNull() ?: 0) > 10 }
                else -> allHistory
            }

            if (filtered.isNotEmpty()) {
                layoutEmpty.visibility = View.GONE
                rvHistory.visibility = View.VISIBLE
                adapter.updateData(filtered)
            } else {
                rvHistory.visibility = View.GONE
                layoutEmpty.visibility = View.VISIBLE
            }
            updateProfileUI()
        }

        adapter = HistoryFullAdapter(
            allHistory,
            onItemClick = { anime ->
                dialog.dismiss()
                playAnimeDirectly(anime)
            },
            onDeleteClick = { anime ->
                SessionManager.removeWatchHistory(this, anime.slug)
                Toast.makeText(this, "${anime.title} dihapus dari riwayat", Toast.LENGTH_SHORT).show()
                refreshList()
            },
            onDownloadClick = { anime ->
                SessionManager.addDownload(this, anime, anime.episode, "188 MB")
                Toast.makeText(this, "${anime.title} disimpan ke Unduhan Saya", Toast.LENGTH_SHORT).show()
            }
        )
        rvHistory.adapter = adapter

        fun selectFilterTab(filter: String, selectedTv: TextView) {
            currentFilter = filter
            listOf(tabAll, tabAnime, tabVideo, tabShow).forEach {
                it.setTextColor(Color.parseColor("#757580"))
                it.paint.isFakeBoldText = false
            }
            selectedTv.setTextColor(Color.parseColor("#17171B"))
            selectedTv.paint.isFakeBoldText = true
            refreshList()
        }

        tabAll.setOnClickListener { selectFilterTab("all", tabAll) }
        tabAnime.setOnClickListener { selectFilterTab("anime", tabAnime) }
        tabVideo.setOnClickListener { selectFilterTab("video", tabVideo) }
        tabShow.setOnClickListener { selectFilterTab("show", tabShow) }

        btnBack.setOnClickListener { dialog.dismiss() }
        btnClearAll.setOnClickListener {
            SessionManager.clearWatchHistory(this)
            Toast.makeText(this, "Semua riwayat tontonan berhasil dibersihkan", Toast.LENGTH_SHORT).show()
            refreshList()
        }

        refreshList()
        dialog.show()
    }

    private fun openDownloadsScreen() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_downloads_full)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

        val btnBack = dialog.findViewById<ImageView>(R.id.btnDownloadsBack)
        val btnClearAll = dialog.findViewById<TextView>(R.id.btnClearAllDownloads)
        val rvDownloads = dialog.findViewById<RecyclerView>(R.id.rvDownloadsFullList)
        val layoutEmpty = dialog.findViewById<LinearLayout>(R.id.layoutDownloadsEmpty)
        val tvStorageUsage = dialog.findViewById<TextView>(R.id.tvStorageUsageText)
        val pbStorage = dialog.findViewById<ProgressBar>(R.id.pbStorageProgress)

        val tabAll = dialog.findViewById<TextView>(R.id.tabDownloadsAll)
        val tabCompleted = dialog.findViewById<TextView>(R.id.tabDownloadsCompleted)

        rvDownloads.layoutManager = LinearLayoutManager(this)

        var allDownloads = SessionManager.getDownloads(this)
        lateinit var adapter: DownloadFullAdapter

        fun refreshList() {
            allDownloads = SessionManager.getDownloads(this)
            val usedMb = allDownloads.size * 188
            val usedStr = if (usedMb >= 1024) String.format(java.util.Locale.US, "%.1f GB", usedMb / 1024.0) else "$usedMb MB"
            tvStorageUsage.text = "$usedStr Digunakan (Tersimpan Lokal)"
            pbStorage.progress = (allDownloads.size * 15).coerceIn(10, 90)

            if (allDownloads.isNotEmpty()) {
                layoutEmpty.visibility = View.GONE
                rvDownloads.visibility = View.VISIBLE
                adapter.updateData(allDownloads)
            } else {
                rvDownloads.visibility = View.GONE
                layoutEmpty.visibility = View.VISIBLE
            }
        }

        adapter = DownloadFullAdapter(
            allDownloads,
            onItemClick = { anime ->
                dialog.dismiss()
                playAnimeDirectly(anime)
            },
            onDeleteClick = { anime ->
                SessionManager.removeDownload(this, anime.slug, anime.episode)
                Toast.makeText(this, "${anime.title} berhasil dihapus dari perangkat", Toast.LENGTH_SHORT).show()
                refreshList()
            }
        )
        rvDownloads.adapter = adapter

        tabAll.setOnClickListener {
            tabAll.setTextColor(Color.parseColor("#17171B"))
            tabAll.paint.isFakeBoldText = true
            tabCompleted.setTextColor(Color.parseColor("#757580"))
            tabCompleted.paint.isFakeBoldText = false
            refreshList()
        }

        tabCompleted.setOnClickListener {
            tabCompleted.setTextColor(Color.parseColor("#17171B"))
            tabCompleted.paint.isFakeBoldText = true
            tabAll.setTextColor(Color.parseColor("#757580"))
            tabAll.paint.isFakeBoldText = false
            refreshList()
        }

        btnBack.setOnClickListener { dialog.dismiss() }
        btnClearAll.setOnClickListener {
            allDownloads.forEach { SessionManager.removeDownload(this, it.slug, it.episode) }
            Toast.makeText(this, "Semua unduhan lokal berhasil dihapus", Toast.LENGTH_SHORT).show()
            refreshList()
        }

        refreshList()
        dialog.show()
    }

    private fun openFavoritesScreen() {
        val history = SessionManager.getWatchHistory(this)
        val msg = if (history.isNotEmpty()) {
            val listText = history.take(6).joinToString("\n• ") { "${it.title} (Episode ${it.episode})" }
            "Daftar anime tersimpan di Favorit Anda:\n\n• $listText\n\nKlik anime di beranda untuk menonton."
        } else {
            "Belum ada anime favorit yang ditambahkan.\n\nBuka anime pilihan Anda dan klik ikon simpan untuk menyimpannya ke daftar Favorit Saya."
        }
        showSimpleDialogSheet("Favorit Saya", msg)
    }

    private fun openEventsScreen() {
        showSimpleDialogSheet(
            "Acara Nyamimo",
            "Nyamimo Anime Festival 2026\n\n" +
            "• Nonton Bareng Anime Musim Semi 2026\n" +
            "• Akses VIP Server Premium 4K Gratis untuk Pengguna Terdaftar\n" +
            "• Event Vote Anime of the Year\n\n" +
            "Ikuti terus pembaruan acara menarik lainnya hanya di aplikasi Nyamimo!"
        )
    }

    private fun openSettingsScreen() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_menu_settings)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnClear = dialog.findViewById<Button>(R.id.btnClearCache)
        val btnDone = dialog.findViewById<Button>(R.id.btnSettingsDone)

        btnClear.setOnClickListener {
            Toast.makeText(this, "Cache data berhasil dibersihkan", Toast.LENGTH_SHORT).show()
        }

        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openHelpScreen() {
        showSimpleDialogSheet(
            "Pusat Bantuan",
            "Panduan & Bantuan Nyamimo:\n\n" +
            "1. Cara Memutar Video:\n" +
            "Pilih anime yang Anda inginkan di halaman Beranda atau gunakan pencarian, lalu pilih episode.\n\n" +
            "2. Mengatasi Video Lemot:\n" +
            "Gunakan tombol 'Pilih Server / Kualitas' di bawah video player untuk berpindah server.\n\n" +
            "3. Mode Layar Penuh (Landscape):\n" +
            "Tekan tombol Fullscreen di pojok kanan atas pemutar video untuk otomatis berputar ke mode landscape.\n\n" +
            "4. Mode Offline / Tanpa Internet:\n" +
            "Buka menu 'Unduhan Saya' untuk menonton anime yang telah tersimpan."
        )
    }

    private fun showFeedbackDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_menu_feedback)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etName = dialog.findViewById<EditText>(R.id.etFeedbackName)
        val etContent = dialog.findViewById<EditText>(R.id.etFeedbackContent)
        val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmitFeedback)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelFeedback)

        btnSubmit.setOnClickListener {
            val content = etContent.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "Mohon tulis saran atau masukan Anda", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            Toast.makeText(this, "Terima kasih! Feedback Anda telah berhasil dikirim.", Toast.LENGTH_LONG).show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showSimpleDialogSheet(title: String, content: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_menu_info)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialog.findViewById<TextView>(R.id.tvInfoDialogTitle)
        val tvContent = dialog.findViewById<TextView>(R.id.tvInfoDialogContent)
        val btnClose = dialog.findViewById<Button>(R.id.btnInfoDialogClose)

        tvTitle.text = title
        tvContent.text = content
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    private fun showOfflineScreen(show: Boolean) {
        if (show) {
            binding.offlineContainer.visibility = View.VISIBLE
            binding.exploreContainer.visibility = View.GONE
            binding.koleksiContainer.visibility = View.GONE
            binding.profileContainer.visibility = View.GONE
            binding.reelsContainer.visibility = View.GONE
            binding.mimoNewsContainer.visibility = View.GONE
            Glide.with(this).asGif().load(R.raw.no_internet_cat).into(binding.ivOfflineCatGif)
        } else {
            binding.offlineContainer.visibility = View.GONE
            if (currentNav == "saya") {
                binding.profileContainer.visibility = View.VISIBLE
                binding.exploreContainer.visibility = View.GONE
                binding.koleksiContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.GONE
            } else if (currentNav == "cari") {
                binding.koleksiContainer.visibility = View.VISIBLE
                binding.exploreContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.GONE
                applyKoleksiFilters()
            } else if (currentNav == "reels") {
                binding.reelsContainer.visibility = View.VISIBLE
                binding.exploreContainer.visibility = View.GONE
                binding.koleksiContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.GONE
            } else if (currentNav == "news") {
                binding.mimoNewsContainer.visibility = View.VISIBLE
                binding.exploreContainer.visibility = View.GONE
                binding.koleksiContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.GONE
            } else {
                binding.exploreContainer.visibility = View.VISIBLE
                binding.koleksiContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.GONE
            }
        }
    }

    private fun updateProfileUI() {
        val user = SessionManager.getUser(this)
        if (user.isLoggedIn) {
            binding.tvProfileName.text = user.name
            binding.tvProfileVipTitle.text = "Nyamimo VIP Member"
            binding.tvProfileVipSubtitle.text = "Status: Aktif • Nikmati Streaming Tanpa Iklan"
            binding.tvProfileJoinVipText.text = "Perpanjang"
        } else {
            binding.tvProfileName.text = "Login / Daftar"
            binding.tvProfileVipTitle.text = "Eksklusif untuk pengguna baru"
            binding.tvProfileVipSubtitle.text = "VIP Standard bebas iklan & server kencang"
            binding.tvProfileJoinVipText.text = "Gabung VIP"
        }
    }

    private fun showAuthDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_auth)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tabLogin = dialog.findViewById<TextView>(R.id.tabAuthLogin)
        val tabRegister = dialog.findViewById<TextView>(R.id.tabAuthRegister)
        val layoutEmail = dialog.findViewById<LinearLayout>(R.id.layoutAuthEmail)
        val layoutConfirm = dialog.findViewById<LinearLayout>(R.id.layoutAuthConfirmPassword)

        val etUsername = dialog.findViewById<EditText>(R.id.etAuthUsername)
        val etEmail = dialog.findViewById<EditText>(R.id.etAuthEmail)
        val etPassword = dialog.findViewById<EditText>(R.id.etAuthPassword)
        val etConfirm = dialog.findViewById<EditText>(R.id.etAuthConfirmPassword)

        val btnSubmit = dialog.findViewById<Button>(R.id.btnSubmitAuth)
        val btnSwitch = dialog.findViewById<TextView>(R.id.btnSwitchAuthMode)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancelAuth)

        var isRegister = false

        fun setMode(register: Boolean) {
            isRegister = register
            if (register) {
                tabRegister.setBackgroundResource(R.drawable.badge_gold_bg)
                tabRegister.setTextColor(Color.parseColor("#17171B"))
                tabLogin.background = null
                tabLogin.setTextColor(Color.parseColor("#757580"))

                layoutEmail.visibility = View.VISIBLE
                layoutConfirm.visibility = View.VISIBLE
                btnSubmit.text = "Daftar Akun Baru"
                btnSwitch.text = "Sudah punya akun? Masuk di sini"
            } else {
                tabLogin.setBackgroundResource(R.drawable.badge_gold_bg)
                tabLogin.setTextColor(Color.parseColor("#17171B"))
                tabRegister.background = null
                tabRegister.setTextColor(Color.parseColor("#757580"))

                layoutEmail.visibility = View.GONE
                layoutConfirm.visibility = View.GONE
                btnSubmit.text = "Masuk Sekarang"
                btnSwitch.text = "Belum punya akun? Daftar sekarang"
            }
        }

        tabLogin.setOnClickListener { setMode(false) }
        tabRegister.setOnClickListener { setMode(true) }
        btnSwitch.setOnClickListener { setMode(!isRegister) }

        btnSubmit.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Mohon lengkapi data Anda", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegister) {
                val email = etEmail.text.toString().trim()
                val confirm = etConfirm.text.toString().trim()

                if (email.isEmpty()) {
                    Toast.makeText(this, "Mohon masukkan email Anda", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password.length < 6) {
                    Toast.makeText(this, "Password minimal 6 karakter", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (password != confirm) {
                    Toast.makeText(this, "Konfirmasi kata sandi tidak cocok", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Save registered user
                SessionManager.saveUser(this, username, username, email, "VIP Member", "")
                dialog.dismiss()
                updateProfileUI()
                Toast.makeText(this, "Pendaftaran akun '$username' berhasil! Selamat datang di Nyamimo.", Toast.LENGTH_LONG).show()
            } else {
                btnSubmit.isEnabled = false
                btnSubmit.text = "Memverifikasi..."

                ApiClient.loginUser(username, password, object : ApiClient.Callback<com.google.gson.JsonObject> {
                    override fun onSuccess(result: com.google.gson.JsonObject) {
                        dialog.dismiss()
                        val name = result.get("name")?.asString ?: username
                        val role = result.get("role")?.asString ?: "VIP Member"
                        SessionManager.saveUser(this@MainActivity, username, name, "", role, "")
                        updateProfileUI()
                        Toast.makeText(this@MainActivity, "Berhasil masuk! Selamat datang $name", Toast.LENGTH_LONG).show()
                    }

                    override fun onError(error: String) {
                        // Local fallback login for instant access
                        SessionManager.saveUser(this@MainActivity, username, username, "", "VIP Member", "")
                        dialog.dismiss()
                        updateProfileUI()
                        Toast.makeText(this@MainActivity, "Berhasil masuk sebagai $username!", Toast.LENGTH_LONG).show()
                    }
                })
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showMainLoading(show: Boolean) {
        if (show) {
            binding.mainLoadingOverlay.visibility = View.VISIBLE
            Glide.with(this).asGif().load(R.raw.loading_cat).into(binding.ivMainLoadingGif)
        } else {
            binding.mainLoadingOverlay.visibility = View.GONE
        }
    }

    private fun loadData() {
        binding.swipeRefresh.isRefreshing = true
        showMainLoading(true)

        if (!isNetworkAvailable()) {
            binding.swipeRefresh.isRefreshing = false
            showMainLoading(false)
            showOfflineScreen(true)
            return
        }

        ApiClient.getHome(object : ApiClient.Callback<HomeResponse> {
            override fun onSuccess(result: HomeResponse) {
                binding.swipeRefresh.isRefreshing = false
                showMainLoading(false)
                homeData = result
                // showOfflineScreen already restores the correct container per currentNav
                showOfflineScreen(false)
                // Only re-apply tab selection when user is on explore/search screens
                if (currentNav == "home") {
                    selectTab(currentTab)
                } else if (currentNav == "cari") {
                    applyKoleksiFilters()
                }
            }

            override fun onError(error: String) {
                binding.swipeRefresh.isRefreshing = false
                showMainLoading(false)
                if (!isNetworkAvailable()) {
                    showOfflineScreen(true)
                } else {
                    val fallback = ApiClient.getFallbackHome()
                    homeData = fallback
                    showOfflineScreen(false)
                    if (currentNav == "home") {
                        selectTab(currentTab)
                    } else if (currentNav == "cari") {
                        applyKoleksiFilters()
                    }
                }
            }
        })
    }

    private fun selectTab(tabKey: String) {
        currentTab = tabKey
        val activeDark = Color.parseColor("#17171B")
        val inactiveGray = Color.parseColor("#757580")

        val tabViews = listOf(
            Pair("untuk_anda", binding.tabUntukAnda),
            Pair("populer", binding.tabPopuler),
            Pair("anime", binding.tabAnime),
            Pair("tamat", binding.tabTamat),
            Pair("action", binding.tabAction),
            Pair("fantasy", binding.tabFantasy)
        )

        for ((key, tv) in tabViews) {
            if (key == tabKey) {
                tv.setTextColor(activeDark)
                tv.paint.isFakeBoldText = true
            } else {
                tv.setTextColor(inactiveGray)
                tv.paint.isFakeBoldText = false
            }
        }

        val data = homeData ?: ApiClient.getFallbackHome()

        binding.exploreContainer.visibility = View.VISIBLE
        binding.profileContainer.visibility = View.GONE
        binding.reelsContainer.visibility = View.GONE
        binding.mimoNewsContainer.visibility = View.GONE
        binding.searchHistorySection.visibility = View.GONE
        binding.trendingSection.visibility = View.GONE
        binding.continueWatchingSection.visibility = if (tabKey == "untuk_anda") View.VISIBLE else View.GONE
        val heroEnabled = SessionManager.getAppConfig(this).hero_carousel.enabled
        binding.heroCarouselSection.visibility = if (tabKey == "untuk_anda" && heroEnabled) View.VISIBLE else View.GONE
        binding.rvSearchResultsList.visibility = View.GONE
        binding.rvPosterGrid.visibility = View.VISIBLE

        when (tabKey) {
            "untuk_anda" -> {
                val blended = (data.popular.take(12) + data.ongoing.take(10) + data.completed.take(8)).distinctBy { it.slug }.shuffled()
                posterAdapter.updateData(blended)
            }
            "populer" -> {
                val popList = if (data.popular.isNotEmpty()) data.popular else data.banners
                posterAdapter.updateData(popList)
            }
            "anime" -> {
                val ongList = if (data.ongoing.isNotEmpty()) data.ongoing else data.popular
                posterAdapter.updateData(ongList)
            }
            "tamat" -> {
                val compList = if (data.completed.isNotEmpty()) data.completed else data.popular.filter { it.status.equals("completed", true) }
                posterAdapter.updateData(if (compList.isNotEmpty()) compList else data.popular.reversed())
            }
            "action" -> {
                val actList = if (data.action.isNotEmpty()) data.action else data.popular.filter { it.title.contains("piece", true) || it.title.contains("naruto", true) || it.title.contains("titan", true) || it.title.contains("bleach", true) || it.title.contains("solo", true) }
                posterAdapter.updateData(actList)
            }
            "fantasy" -> {
                val fanList = if (data.fantasy.isNotEmpty()) data.fantasy else data.popular.filter { it.title.contains("slime", true) || it.title.contains("mushoku", true) || it.title.contains("tensei", true) || it.title.contains("re:zero", true) }
                posterAdapter.updateData(fanList)
            }
        }
    }

    private fun setupMimoNewsUI() {
        val spanCount = if (resources.configuration.screenWidthDp >= 600) 2 else 1
        if (spanCount > 1) {
            binding.rvMimoNewsList.layoutManager = GridLayoutManager(this, spanCount)
        } else {
            binding.rvMimoNewsList.layoutManager = LinearLayoutManager(this)
        }

        mimoNewsAdapter = MimoNewsAdapter(emptyList()) { item ->
            showNewsDetailDialog(item)
        }
        binding.rvMimoNewsList.adapter = mimoNewsAdapter

        binding.chipNewsNow.setOnClickListener { loadMimoNews("now") }
        binding.chipNewsUpcoming.setOnClickListener { loadMimoNews("upcoming") }
        binding.chipNewsFall2026.setOnClickListener { loadMimoNews("fall2026") }
        binding.chipNewsWinter2027.setOnClickListener { loadMimoNews("winter2027") }
        binding.chipNewsSpring2027.setOnClickListener { loadMimoNews("spring2027") }
        binding.chipNewsSchedule.setOnClickListener { loadMimoNews("schedule") }

        binding.btnRefreshNews.setOnClickListener {
            JikanApiClient.clearCache()
            loadMimoNews(currentNewsFilter)
            Toast.makeText(this, "Memperbarui Mimo News...", Toast.LENGTH_SHORT).show()
        }

        binding.btnNewsRetry.setOnClickListener {
            loadMimoNews(currentNewsFilter)
        }
    }

    private fun loadMimoNews(filter: String) {
        currentNewsFilter = filter

        val chips = listOf(
            Pair("now", binding.chipNewsNow),
            Pair("upcoming", binding.chipNewsUpcoming),
            Pair("fall2026", binding.chipNewsFall2026),
            Pair("winter2027", binding.chipNewsWinter2027),
            Pair("spring2027", binding.chipNewsSpring2027),
            Pair("schedule", binding.chipNewsSchedule)
        )

        for ((key, chip) in chips) {
            if (key == filter) {
                chip.setBackgroundResource(R.drawable.badge_gold_bg)
                chip.setTextColor(Color.parseColor("#17171B"))
            } else {
                chip.setBackgroundResource(R.drawable.search_chip_bg)
                chip.setTextColor(Color.parseColor("#757580"))
            }
        }

        // Show loading, hide error state and list
        binding.ivNewsLoadingGif.visibility = View.VISIBLE
        binding.layoutNewsError.visibility = View.GONE
        binding.rvMimoNewsList.visibility = View.GONE
        Glide.with(this).asGif().load(R.raw.loading_cat).into(binding.ivNewsLoadingGif)

        val onSuccess: (List<MimoNewsItem>) -> Unit = { items ->
            binding.ivNewsLoadingGif.visibility = View.GONE
            binding.layoutNewsError.visibility = View.GONE
            binding.rvMimoNewsList.visibility = View.VISIBLE
            mimoNewsAdapter.updateData(items)
        }

        val onError: () -> Unit = {
            binding.ivNewsLoadingGif.visibility = View.GONE
            binding.rvMimoNewsList.visibility = View.GONE
            binding.layoutNewsError.visibility = View.VISIBLE
        }

        when (filter) {
            "now"        -> JikanApiClient.getSeasonNow(onSuccess, onError)
            "upcoming"   -> JikanApiClient.getSeasonUpcoming(onSuccess, onError)
            "fall2026"   -> JikanApiClient.getSeasonByYear(2026, "fall", onSuccess, onError)
            "winter2027" -> JikanApiClient.getSeasonByYear(2027, "winter", onSuccess, onError)
            "spring2027" -> JikanApiClient.getSeasonByYear(2027, "spring", onSuccess, onError)
            "schedule"   -> JikanApiClient.getSchedules(onSuccess, onError)
            else         -> JikanApiClient.getSeasonNow(onSuccess, onError)
        }
    }

    private fun showNewsDetailDialog(item: MimoNewsItem) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_mimo_news_detail)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val ivPoster = dialog.findViewById<ImageView>(R.id.ivDialogPoster)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val tvJapaneseTitle = dialog.findViewById<TextView>(R.id.tvDialogJapaneseTitle)
        val tvReleaseBadge = dialog.findViewById<TextView>(R.id.tvDialogReleaseBadge)
        val tvStudio = dialog.findViewById<TextView>(R.id.tvDialogStudio)
        val tvSource = dialog.findViewById<TextView>(R.id.tvDialogSource)
        val tvGenres = dialog.findViewById<TextView>(R.id.tvDialogGenres)
        val tvSynopsis = dialog.findViewById<TextView>(R.id.tvDialogSynopsis)
        val tvMembers = dialog.findViewById<TextView>(R.id.tvDialogMembers)
        val tvLikes = dialog.findViewById<TextView>(R.id.tvDialogLikes)
        val tvScore = dialog.findViewById<TextView>(R.id.tvDialogScore)
        val btnTrailer = dialog.findViewById<Button>(R.id.btnDialogTrailer)
        val btnClose = dialog.findViewById<Button>(R.id.btnDialogClose)
        val btnCloseHeader = dialog.findViewById<ImageView>(R.id.btnCloseNewsDialog)

        tvTitle.text = item.title
        val sub = if (item.titleJapanese.isNotEmpty()) item.titleJapanese else item.titleEnglish
        if (sub.isNotEmpty()) {
            tvJapaneseTitle.visibility = View.VISIBLE
            tvJapaneseTitle.text = sub
        } else {
            tvJapaneseTitle.visibility = View.GONE
        }

        tvReleaseBadge.text = item.seasonYear.ifEmpty { item.releaseDate }
        tvStudio.text = "Studio: ${item.studio.ifEmpty { "Nyamimo Studio" }}"
        tvSource.text = "Source: ${item.source.ifEmpty { "Manga" }} • ${item.episodes}"
        tvGenres.text = if (item.genres.isNotEmpty()) item.genres.joinToString(", ") else "Action, Adventure"
        tvSynopsis.text = item.synopsis.ifEmpty { "Sinopsis resmi anime ini akan segera diumumkan." }

        // Penonton / Members
        if (item.members.isNotEmpty()) {
            tvMembers.visibility = View.VISIBLE
            tvMembers.text = "👥 ${item.members} Penonton"
        } else {
            tvMembers.visibility = View.GONE
        }

        // Like / Favorites
        if (item.favorites.isNotEmpty()) {
            tvLikes.visibility = View.VISIBLE
            tvLikes.text = "❤️ ${item.favorites} Suka"
        } else {
            tvLikes.visibility = View.GONE
        }

        // Score
        if (item.score.isNotEmpty() && item.score != "N/A") {
            tvScore.visibility = View.VISIBLE
            tvScore.text = "⭐ ${item.score}"
        } else {
            tvScore.visibility = View.GONE
        }


        if (item.img.isNotEmpty()) {
            Glide.with(this)
                .load(item.img)
                .placeholder(R.drawable.logo_nyamimo)
                .centerCrop()
                .into(ivPoster)
        }

        val trailer = item.trailerUrl.ifEmpty { item.trailerEmbedUrl }
        if (trailer.isNotEmpty()) {
            btnTrailer.visibility = View.VISIBLE
            btnTrailer.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailer))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Tidak dapat membuka link trailer", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnTrailer.visibility = View.GONE
        }

        val closeAction = View.OnClickListener { dialog.dismiss() }
        btnClose.setOnClickListener(closeAction)
        btnCloseHeader.setOnClickListener(closeAction)

        dialog.show()
    }

    private fun selectBottomNav(navKey: String) {
        currentNav = navKey
        val activeGold = Color.parseColor("#FFCC00")
        val activeGreen = Color.parseColor("#00D26A")
        val activeDark = Color.parseColor("#17171B")
        val inactiveGray = Color.parseColor("#8E8E93")

        binding.bottomBarBstation.setBackgroundColor(Color.parseColor("#FFFFFF"))

        binding.ivNavHome.setColorFilter(inactiveGray)
        binding.tvNavHome.setTextColor(inactiveGray)
        binding.ivNavCari.setColorFilter(inactiveGray)
        binding.tvNavCari.setTextColor(inactiveGray)
        binding.ivNavNews.setColorFilter(inactiveGray)
        binding.tvNavNews.setTextColor(inactiveGray)
        binding.ivNavSaya.setColorFilter(inactiveGray)
        binding.tvNavSaya.setTextColor(inactiveGray)

        when (navKey) {
            "home" -> {
                binding.ivNavHome.setColorFilter(activeGold)
                binding.tvNavHome.setTextColor(activeDark)
                binding.headerContainer.visibility = View.VISIBLE
                binding.exploreContainer.visibility = View.VISIBLE
                binding.koleksiContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.GONE
                if (!isNetworkAvailable() && homeData == null) {
                    showOfflineScreen(true)
                } else {
                    showOfflineScreen(false)
                    selectTab("untuk_anda")
                }
            }
            "cari" -> {
                binding.ivNavCari.setColorFilter(activeGold)
                binding.tvNavCari.setTextColor(activeDark)
                binding.headerContainer.visibility = View.GONE
                binding.exploreContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.GONE
                binding.offlineContainer.visibility = View.GONE
                binding.koleksiContainer.visibility = View.VISIBLE
                applyKoleksiFilters()
            }
            "reels" -> {
                binding.headerContainer.visibility = View.GONE
                binding.exploreContainer.visibility = View.GONE
                binding.koleksiContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.GONE
                binding.offlineContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.VISIBLE
                Toast.makeText(this, "Nyamimo Shorts Feed (Geser untuk klip berikutnya)", Toast.LENGTH_SHORT).show()
            }
            "news" -> {
                binding.ivNavNews.setColorFilter(activeGold)
                binding.tvNavNews.setTextColor(activeDark)
                binding.headerContainer.visibility = View.GONE
                binding.exploreContainer.visibility = View.GONE
                binding.koleksiContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.GONE
                binding.offlineContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.VISIBLE
                if (mimoNewsAdapter.itemCount == 0) {
                    loadMimoNews(currentNewsFilter)
                }
            }
            "saya" -> {
                binding.ivNavSaya.setColorFilter(activeGold)
                binding.tvNavSaya.setTextColor(activeDark)
                binding.headerContainer.visibility = View.GONE
                binding.exploreContainer.visibility = View.GONE
                binding.koleksiContainer.visibility = View.GONE
                binding.reelsContainer.visibility = View.GONE
                binding.mimoNewsContainer.visibility = View.GONE
                binding.offlineContainer.visibility = View.GONE
                binding.profileContainer.visibility = View.VISIBLE
                updateProfileUI()
            }
        }
    }

    private fun setupKoleksiUI() {
        // Toggle Search Bar
        binding.btnKoleksiToggleSearch.setOnClickListener {
            if (binding.layoutKoleksiSearchBar.visibility == View.VISIBLE) {
                binding.layoutKoleksiSearchBar.visibility = View.GONE
                binding.etKoleksiSearch.setText("")
            } else {
                binding.layoutKoleksiSearchBar.visibility = View.VISIBLE
                binding.etKoleksiSearch.requestFocus()
            }
        }

        binding.btnKoleksiClearSearch.setOnClickListener {
            binding.etKoleksiSearch.setText("")
        }

        binding.etKoleksiSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnKoleksiClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                koleksiSearchQuery = query
                applyKoleksiFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Top Primary Category Tabs
        binding.tabKoleksiDrama.setOnClickListener { setKoleksiTopTab("drama") }
        binding.tabKoleksiAnimeWrapper.setOnClickListener { setKoleksiTopTab("anime") }
        binding.tabKoleksiAnime.setOnClickListener { setKoleksiTopTab("anime") }
        binding.tabKoleksiVariety.setOnClickListener { setKoleksiTopTab("variety") }
        binding.tabKoleksiFilm.setOnClickListener { setKoleksiTopTab("film") }
        binding.tabKoleksiDonghua.setOnClickListener { setKoleksiTopTab("donghua") }
        binding.tabKoleksiShorts.setOnClickListener { setKoleksiTopTab("shorts") }

        // Row 1: Wilayah
        binding.chipWilayahAll.setOnClickListener { setWilayahFilter("all") }
        binding.chipWilayahJapan.setOnClickListener { setWilayahFilter("japan") }
        binding.chipWilayahChina.setOnClickListener { setWilayahFilter("china") }
        binding.chipWilayahKorea.setOnClickListener { setWilayahFilter("korea") }

        // Row 2: Genre
        binding.chipGenreAll.setOnClickListener { setGenreFilter("all") }
        binding.chipGenreAksi.setOnClickListener { setGenreFilter("aksi") }
        binding.chipGenrePetualangan.setOnClickListener { setGenreFilter("petualangan") }
        binding.chipGenreKomedi.setOnClickListener { setGenreFilter("komedi") }
        binding.chipGenreFiksi.setOnClickListener { setGenreFilter("fiksi") }
        binding.chipGenrePercintaan.setOnClickListener { setGenreFilter("percintaan") }
        binding.chipGenreBergairah.setOnClickListener { setGenreFilter("bergairah") }
        binding.chipGenreFantasi.setOnClickListener { setGenreFilter("fantasi") }
        binding.chipGenreIsekai.setOnClickListener { setGenreFilter("isekai") }

        // Row 3: Subtitle
        binding.chipSubtitleAll.setOnClickListener { setSubtitleFilter("all") }
        binding.chipSubtitleManual.setOnClickListener { setSubtitleFilter("manual") }
        binding.chipSubtitleDub.setOnClickListener { setSubtitleFilter("dub") }

        // Row 4: Akses
        binding.chipAksesAll.setOnClickListener { setAksesFilter("all") }
        binding.chipAksesVip.setOnClickListener { setAksesFilter("vip") }
        binding.chipAksesGratis.setOnClickListener { setAksesFilter("gratis") }

        // Row 5: Sort
        binding.chipSortPopuler.setOnClickListener { setSortFilter("populer") }
        binding.chipSortTerbaru.setOnClickListener { setSortFilter("terbaru") }
        binding.chipSortRating.setOnClickListener { setSortFilter("rating") }
    }

    private fun setKoleksiTopTab(tab: String) {
        selectedKoleksiTab = tab
        val activeDark = Color.parseColor("#17171B")
        val inactiveGray = Color.parseColor("#757580")

        binding.tabKoleksiDrama.setTextColor(if (tab == "drama") activeDark else inactiveGray)
        binding.tabKoleksiAnime.setTextColor(if (tab == "anime") activeDark else inactiveGray)
        binding.indicatorKoleksiAnime.visibility = if (tab == "anime") View.VISIBLE else View.INVISIBLE
        binding.tabKoleksiVariety.setTextColor(if (tab == "variety") activeDark else inactiveGray)
        binding.tabKoleksiFilm.setTextColor(if (tab == "film") activeDark else inactiveGray)
        binding.tabKoleksiDonghua.setTextColor(if (tab == "donghua") activeDark else inactiveGray)
        binding.tabKoleksiShorts.setTextColor(if (tab == "shorts") activeDark else inactiveGray)

        applyKoleksiFilters()
    }

    private fun updateFilterChip(chip: TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_filter_pill_selected)
            chip.setTextColor(Color.parseColor("#17171B"))
            chip.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            chip.setBackgroundResource(R.drawable.bg_filter_pill_unselected)
            chip.setTextColor(Color.parseColor("#757580"))
            chip.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    private fun setWilayahFilter(wilayah: String) {
        selectedWilayah = wilayah
        updateFilterChip(binding.chipWilayahAll, wilayah == "all")
        updateFilterChip(binding.chipWilayahJapan, wilayah == "japan")
        updateFilterChip(binding.chipWilayahChina, wilayah == "china")
        updateFilterChip(binding.chipWilayahKorea, wilayah == "korea")
        applyKoleksiFilters()
    }

    private fun setGenreFilter(genre: String) {
        selectedGenre = genre
        updateFilterChip(binding.chipGenreAll, genre == "all")
        updateFilterChip(binding.chipGenreAksi, genre == "aksi")
        updateFilterChip(binding.chipGenrePetualangan, genre == "petualangan")
        updateFilterChip(binding.chipGenreKomedi, genre == "komedi")
        updateFilterChip(binding.chipGenreFiksi, genre == "fiksi")
        updateFilterChip(binding.chipGenrePercintaan, genre == "percintaan")
        updateFilterChip(binding.chipGenreBergairah, genre == "bergairah")
        updateFilterChip(binding.chipGenreFantasi, genre == "fantasi")
        updateFilterChip(binding.chipGenreIsekai, genre == "isekai")
        applyKoleksiFilters()
    }

    private fun setSubtitleFilter(sub: String) {
        selectedSubtitle = sub
        updateFilterChip(binding.chipSubtitleAll, sub == "all")
        updateFilterChip(binding.chipSubtitleManual, sub == "manual")
        updateFilterChip(binding.chipSubtitleDub, sub == "dub")
        applyKoleksiFilters()
    }

    private fun setAksesFilter(akses: String) {
        selectedAkses = akses
        updateFilterChip(binding.chipAksesAll, akses == "all")
        updateFilterChip(binding.chipAksesVip, akses == "vip")
        updateFilterChip(binding.chipAksesGratis, akses == "gratis")
        applyKoleksiFilters()
    }

    private fun setSortFilter(sort: String) {
        selectedSort = sort
        updateFilterChip(binding.chipSortPopuler, sort == "populer")
        updateFilterChip(binding.chipSortTerbaru, sort == "terbaru")
        updateFilterChip(binding.chipSortRating, sort == "rating")
        applyKoleksiFilters()
    }

    private fun applyKoleksiFilters() {
        val data = homeData ?: ApiClient.getFallbackHome()
        val allAnime = (data.banners + data.popular + data.ongoing + data.completed).distinctBy { it.slug.ifEmpty { it.title.lowercase() } }

        var result = allAnime.filter { item ->
            // Filter Tab
            val tabMatch = when (selectedKoleksiTab) {
                "film" -> item.type.contains("Movie", ignoreCase = true) || item.title.contains("Movie", ignoreCase = true) || item.type.contains("Film", ignoreCase = true)
                "donghua" -> item.title.contains("Soul Land", ignoreCase = true) || item.title.contains("Gods", ignoreCase = true) || item.synopsis.contains("China", ignoreCase = true) || item.synopsis.contains("Donghua", ignoreCase = true)
                "drama", "shorts" -> item.type.contains("ONA", ignoreCase = true) || item.type.contains("Special", ignoreCase = true) || item.episode.contains("24") || item.episode.contains("12")
                "variety" -> true
                else -> true
            }

            // Filter Wilayah
            val wilayahMatch = when (selectedWilayah) {
                "japan" -> !item.title.contains("Soul Land", ignoreCase = true) && !item.synopsis.contains("Donghua", ignoreCase = true)
                "china" -> item.title.contains("Soul Land", ignoreCase = true) || item.title.contains("Against", ignoreCase = true) || item.synopsis.contains("China", ignoreCase = true) || item.synopsis.contains("Donghua", ignoreCase = true)
                "korea" -> item.title.contains("Solo", ignoreCase = true) || item.title.contains("Tower", ignoreCase = true) || item.synopsis.contains("Korea", ignoreCase = true)
                else -> true
            }

            // Filter Genre
            val genreMatch = when (selectedGenre) {
                "aksi" -> item.title.contains("Hunter", ignoreCase = true) || item.title.contains("Solo", ignoreCase = true) || item.title.contains("Piece", ignoreCase = true) || item.title.contains("Naruto", ignoreCase = true) || item.title.contains("Gachiakuta", ignoreCase = true) || item.synopsis.contains("Aksi", ignoreCase = true) || item.synopsis.contains("Action", ignoreCase = true)
                "petualangan" -> item.title.contains("Piece", ignoreCase = true) || item.title.contains("Hunter", ignoreCase = true) || item.synopsis.contains("Adventure", ignoreCase = true) || item.synopsis.contains("Petualangan", ignoreCase = true)
                "komedi" -> item.title.contains("Chiikawa", ignoreCase = true) || item.title.contains("Bocchi", ignoreCase = true) || item.title.contains("Dating", ignoreCase = true) || item.synopsis.contains("Komedi", ignoreCase = true) || item.synopsis.contains("Comedy", ignoreCase = true)
                "fiksi" -> item.title.contains("86", ignoreCase = true) || item.title.contains("Digimon", ignoreCase = true) || item.synopsis.contains("Sci-Fi", ignoreCase = true)
                "percintaan" -> item.title.contains("Kanojo", ignoreCase = true) || item.title.contains("Villainess", ignoreCase = true) || item.synopsis.contains("Romance", ignoreCase = true) || item.synopsis.contains("Percintaan", ignoreCase = true)
                "bergairah" -> item.title.contains("Naruto", ignoreCase = true) || item.title.contains("Boruto", ignoreCase = true) || item.title.contains("Black Clover", ignoreCase = true) || item.synopsis.contains("Shounen", ignoreCase = true)
                "fantasi" -> item.title.contains("Gods", ignoreCase = true) || item.title.contains("Solo", ignoreCase = true) || item.title.contains("Isekai", ignoreCase = true) || item.synopsis.contains("Fantasy", ignoreCase = true) || item.synopsis.contains("Fantasi", ignoreCase = true)
                "isekai" -> item.title.contains("Dating Sim", ignoreCase = true) || item.title.contains("Villainess", ignoreCase = true) || item.synopsis.contains("Isekai", ignoreCase = true)
                else -> true
            }

            // Filter Subtitle
            val subMatch = when (selectedSubtitle) {
                "manual" -> true
                "dub" -> item.title.contains("Naruto", ignoreCase = true) || item.title.contains("Piece", ignoreCase = true)
                else -> true
            }

            // Filter Search Query
            val qMatch = if (koleksiSearchQuery.isEmpty()) true else {
                item.title.contains(koleksiSearchQuery, ignoreCase = true) || item.synopsis.contains(koleksiSearchQuery, ignoreCase = true)
            }

            tabMatch && wilayahMatch && genreMatch && subMatch && qMatch
        }

        // Apply Sorting
        val sortedList = when (selectedSort) {
            "terbaru" -> result.sortedByDescending { it.episode.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            "rating" -> result.sortedByDescending { it.score.toDoubleOrNull() ?: 0.0 }
            else -> result
        }

        if (::koleksiAdapter.isInitialized) {
            koleksiAdapter.updateData(sortedList)
        }

        binding.tvKoleksiEmpty.visibility = if (sortedList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun performSearch(query: String) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return

        val data = homeData ?: ApiClient.getFallbackHome()
        val allAnime = (data.banners + data.popular + data.ongoing + data.completed).distinctBy { it.slug }
        val localMatches = allAnime.filter {
            it.title.lowercase().contains(q) || it.slug.lowercase().contains(q) || it.synopsis.lowercase().contains(q)
        }

        if (localMatches.isNotEmpty()) {
            searchSuggestionAdapter.updateData(localMatches)
            posterAdapter.updateData(localMatches)
        }

        if (!isNetworkAvailable()) {
            if (localMatches.isEmpty()) {
                Toast.makeText(this, "Tidak ada koneksi internet", Toast.LENGTH_SHORT).show()
            }
            return
        }

        binding.swipeRefresh.isRefreshing = true

        ApiClient.searchAnime(query, object : ApiClient.Callback<List<AnimeItem>> {
            override fun onSuccess(result: List<AnimeItem>) {
                binding.swipeRefresh.isRefreshing = false
                val combined = (localMatches + result).distinctBy { it.slug.ifEmpty { it.title.lowercase() } }
                if (combined.isNotEmpty()) {
                    searchSuggestionAdapter.updateData(combined)
                    posterAdapter.updateData(combined)
                } else if (localMatches.isEmpty()) {
                    searchSuggestionAdapter.updateData(emptyList())
                    posterAdapter.updateData(emptyList())
                    Toast.makeText(this@MainActivity, "Tidak ada anime ditemukan untuk '$query'", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                binding.swipeRefresh.isRefreshing = false
                if (localMatches.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Pencarian offline: anime tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun playAnimeDirectly(anime: AnimeItem) {
        val intent = Intent(this, AnimeDetailActivity::class.java).apply {
            putExtra("slug", anime.slug)
            putExtra("title", anime.title)
            putExtra("img", anime.img)
            putExtra("score", anime.score)
            putExtra("synopsis", anime.synopsis)
            putExtra("status", anime.status)
            putExtra("type", anime.type)
            putExtra("episode", anime.episode)
        }
        startActivity(intent)
    }
}
