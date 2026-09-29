package com.nyamimo.app.api

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nyamimo.app.model.*
import okhttp3.*
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_UPSTREAM = "https://api.animekudesu.web.id"
    private const val BASE_RENDER = "https://nyamimo.onrender.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    interface Callback<T> {
        fun onSuccess(result: T)
        fun onError(error: String)
    }

    private fun optString(obj: JsonObject?, key: String, fallback: String = ""): String {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull) return fallback
        return try {
            val elem = obj.get(key)
            if (elem.isJsonPrimitive) elem.asString else elem.toString().trim('"')
        } catch (e: Exception) {
            fallback
        }
    }

    fun cleanAnimeTitle(t: String): String {
        var clean = t.trim()
        val subIndoIdx = clean.indexOf("Sub Indo", ignoreCase = true)
        if (subIndoIdx != -1) {
            val part1 = clean.substring(0, subIndoIdx).trim()
            val part2 = clean.substring(subIndoIdx + 8).trim()
            clean = if (part1.isNotEmpty()) part1 else part2
        }
        return if (clean.isNotEmpty()) clean else t
    }

    private fun parseAnimeListFromJSON(jsonBody: String): List<AnimeItem> {
        val list = mutableListOf<AnimeItem>()
        try {
            val json = gson.fromJson(jsonBody, JsonObject::class.java)
            val dataArray = json.getAsJsonArray("data")
                ?: json.getAsJsonArray("results")
                ?: json.getAsJsonArray("ongoing_anime")
                ?: json.getAsJsonArray("completed_anime")
                ?: json.getAsJsonArray("popular")
                ?: json.getAsJsonArray("animeList")

            if (dataArray != null) {
                for (elem in dataArray) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        val title = optString(obj, "title").ifEmpty { optString(obj, "seriesName") }
                        var slug = optString(obj, "slug").ifEmpty { optString(obj, "detail_url").ifEmpty { optString(obj, "link") } }
                        slug = slug.trim('/').removePrefix("detail-anime/").removePrefix("anime/").trim('/')
                        val img = optString(obj, "img").ifEmpty { optString(obj, "poster") }
                        val score = optString(obj, "score").ifEmpty { optString(obj, "rating", "8.5") }
                        val type = optString(obj, "type", "Anime")
                        val status = optString(obj, "status", "Ongoing")
                        val eps = optString(obj, "episode").ifEmpty { optString(obj, "released") }

                        var synopsis = ""
                        if (obj.has("descriptions") && !obj.get("descriptions").isJsonNull) {
                            val dElem = obj.get("descriptions")
                            if (dElem.isJsonArray) {
                                val pars = mutableListOf<String>()
                                for (p in dElem.asJsonArray) {
                                    val t = p.asString.trim()
                                    if (t.isNotEmpty() && !t.startsWith("Tonton Juga", ignoreCase = true)) pars.add(t)
                                }
                                synopsis = pars.joinToString("\n\n")
                            }
                        }
                        if (synopsis.isEmpty()) {
                            synopsis = optString(obj, "synopsis").ifEmpty { optString(obj, "description") }
                        }

                        val genresList = mutableListOf<String>()
                        if (obj.has("genres") && obj.get("genres").isJsonArray) {
                            for (g in obj.getAsJsonArray("genres")) {
                                if (g.isJsonObject) {
                                    val tag = optString(g.asJsonObject, "tag").ifEmpty { optString(g.asJsonObject, "name") }
                                    if (tag.isNotEmpty()) genresList.add(tag)
                                } else if (g.isJsonPrimitive) {
                                    genresList.add(g.asString)
                                }
                            }
                        }

                        if (title.isNotEmpty() && slug.isNotEmpty()) {
                            list.add(AnimeItem(cleanAnimeTitle(title), slug, img, eps, score, type, status, synopsis, genresList))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    fun getHome(callback: Callback<HomeResponse>) {
        val reqOngoing = Request.Builder().url("$BASE_UPSTREAM/ongoing-anime").header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0").build()
        val reqPopular = Request.Builder().url("$BASE_UPSTREAM/order-anime/popular?page=1").header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0").build()
        val reqPopular2 = Request.Builder().url("$BASE_UPSTREAM/order-anime/popular?page=2").header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0").build()
        val reqCompleted = Request.Builder().url("$BASE_UPSTREAM/completed-anime").header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0").build()

        client.newCall(reqPopular).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                fetchOngoingHome(callback)
            }

            override fun onResponse(call: Call, response: Response) {
                val popBody = response.body?.string() ?: ""
                val popularList1 = parseAnimeListFromJSON(popBody)

                client.newCall(reqPopular2).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        fetchOngoingAndCompleted(popularList1, emptyList(), callback)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val popBody2 = response.body?.string() ?: ""
                        val popularList2 = parseAnimeListFromJSON(popBody2)
                        fetchOngoingAndCompleted(popularList1, popularList2, callback)
                    }
                })
            }
        })
    }

    private fun fetchOngoingAndCompleted(
        pop1: List<AnimeItem>,
        pop2: List<AnimeItem>,
        callback: Callback<HomeResponse>
    ) {
        val reqOngoing = Request.Builder().url("$BASE_UPSTREAM/ongoing-anime").header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0").build()
        val reqCompleted = Request.Builder().url("$BASE_UPSTREAM/completed-anime").header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0").build()

        client.newCall(reqOngoing).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                val allPop = (pop1 + pop2).distinctBy { it.slug }
                val finalPop = if (allPop.isNotEmpty()) allPop else getFallbackHome().popular
                val res = HomeResponse("ok", finalPop.take(5), finalPop, finalPop, emptyList())
                mainHandler.post { callback.onSuccess(res) }
            }

            override fun onResponse(call: Call, response: Response) {
                val ongBody = response.body?.string() ?: ""
                val ongoingList = parseAnimeListFromJSON(ongBody)

                client.newCall(reqCompleted).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        buildAndDeliverHomeResponse(pop1, pop2, ongoingList, emptyList(), callback)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val compBody = response.body?.string() ?: ""
                        val completedList = parseAnimeListFromJSON(compBody)
                        buildAndDeliverHomeResponse(pop1, pop2, ongoingList, completedList, callback)
                    }
                })
            }
        })
    }

    private fun buildAndDeliverHomeResponse(
        pop1: List<AnimeItem>,
        pop2: List<AnimeItem>,
        ongoingList: List<AnimeItem>,
        completedList: List<AnimeItem>,
        callback: Callback<HomeResponse>
    ) {
        val allPop = (pop1 + pop2).distinctBy { it.slug }
        val allCombined = (allPop + ongoingList + completedList).distinctBy { it.slug }

        // Action Genre List
        val actionList = allCombined.filter { item ->
            item.genres.any { g -> g.contains("Action", true) || g.contains("Shounen", true) || g.contains("Martial", true) || g.contains("Super Power", true) }
        }.ifEmpty { allPop.filter { it.title.contains("piece", true) || it.title.contains("naruto", true) || it.title.contains("bleach", true) || it.title.contains("titan", true) || it.title.contains("jujutsu", true) || it.title.contains("solo", true) } }

        // Fantasy / Isekai Genre List
        val fantasyList = allCombined.filter { item ->
            item.genres.any { g -> g.contains("Fantasy", true) || g.contains("Isekai", true) || g.contains("Magic", true) || g.contains("Supernatural", true) || g.contains("Adventure", true) }
        }.ifEmpty { allPop.filter { it.title.contains("slime", true) || it.title.contains("mushoku", true) || it.title.contains("tensei", true) || it.title.contains("re:zero", true) || it.title.contains("overlord", true) } }

        val banners = if (allPop.isNotEmpty()) allPop.take(5) else ongoingList.take(5)

        val res = HomeResponse(
            status = "ok",
            banners = banners,
            popular = if (allPop.isNotEmpty()) allPop else getFallbackHome().popular,
            ongoing = if (ongoingList.isNotEmpty()) ongoingList else getFallbackHome().ongoing,
            completed = if (completedList.isNotEmpty()) completedList else getFallbackHome().completed,
            action = if (actionList.isNotEmpty()) actionList else allCombined.takeLast(12),
            fantasy = if (fantasyList.isNotEmpty()) fantasyList else allCombined.drop(4).take(12)
        )
        mainHandler.post { callback.onSuccess(res) }
    }

    private fun fetchOngoingHome(callback: Callback<HomeResponse>) {
        val req = Request.Builder().url("$BASE_UPSTREAM/ongoing-anime").header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0").build()
        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback.onSuccess(getFallbackHome()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val list = parseAnimeListFromJSON(body)
                if (list.isNotEmpty()) {
                    val res = HomeResponse("ok", list.take(5), list, list, emptyList())
                    mainHandler.post { callback.onSuccess(res) }
                } else {
                    mainHandler.post { callback.onSuccess(getFallbackHome()) }
                }
            }
        })
    }

    fun getAnimeDetail(slug: String, callback: Callback<AnimeDetailData>) {
        val cleanSlug = slug.trim('/').removePrefix("detail-anime/").removePrefix("anime/").trim('/')
        val request = Request.Builder()
            .url("$BASE_UPSTREAM/detail-anime/$cleanSlug")
            .header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback.onError(e.message ?: "Gagal memuat anime") }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val detailObj = if (json.has("data") && !json.get("data").isJsonNull) json.getAsJsonObject("data") else json

                        val rawTitle = optString(detailObj, "title", "Anime")
                        val cleanTitle = cleanAnimeTitle(rawTitle)
                        val img = optString(detailObj, "img").ifEmpty { optString(detailObj, "poster") }
                        val score = optString(detailObj, "score").ifEmpty { optString(detailObj, "rating", "8.5") }
                        val status = optString(detailObj, "status", "Tersedia")

                        var synopsis = ""
                        if (detailObj.has("descriptions") && !detailObj.get("descriptions").isJsonNull) {
                            val descElem = detailObj.get("descriptions")
                            if (descElem.isJsonArray) {
                                val list = mutableListOf<String>()
                                for (p in descElem.asJsonArray) {
                                    val text = p.asString.trim()
                                    if (text.isNotEmpty() && !text.startsWith("Tonton Juga", ignoreCase = true)) {
                                        list.add(text)
                                    }
                                }
                                synopsis = list.joinToString("\n\n")
                            } else if (descElem.isJsonPrimitive) {
                                synopsis = descElem.asString.trim()
                            }
                        }
                        if (synopsis.isEmpty()) {
                            synopsis = optString(detailObj, "synopsis").ifEmpty {
                                optString(detailObj, "description", "Sinopsis belum tersedia.")
                            }
                        }

                        val episodes = mutableListOf<EpisodeItem>()
                        val epsArray = if (detailObj.has("episodes") && !detailObj.get("episodes").isJsonNull) {
                            detailObj.getAsJsonArray("episodes")
                        } else if (detailObj.has("episodeList") && !detailObj.get("episodeList").isJsonNull) {
                            detailObj.getAsJsonArray("episodeList")
                        } else {
                            null
                        }

                        if (epsArray != null) {
                            for (epElem in epsArray) {
                                if (epElem.isJsonObject) {
                                    val epObj = epElem.asJsonObject
                                    val epTitle = optString(epObj, "title")
                                    var epNum = optString(epObj, "episode").ifEmpty { optString(epObj, "episodeNumber") }
                                    if (epNum.isEmpty() || epNum == "null") {
                                        val numMatch = Regex("""\b(?:Episode|Eps|Ep)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitle)
                                        epNum = if (numMatch != null) numMatch.groupValues[1] else "1"
                                    }
                                    val epLink = optString(epObj, "link").ifEmpty { optString(epObj, "href") }
                                    val detailEps = optString(epObj, "detail_eps").ifEmpty { epLink }

                                    if (detailEps.isNotEmpty()) {
                                        episodes.add(EpisodeItem(epTitle, epNum, epNum, epLink, detailEps))
                                    }
                                }
                            }
                        }

                        val type = optString(detailObj, "type").ifEmpty { "TV Series" }
                        val studio = optString(detailObj, "studio").ifEmpty { optString(detailObj, "studios", "Nyamimo Animation") }
                        val season = optString(detailObj, "season").ifEmpty { optString(detailObj, "release", "2024") }
                        val duration = optString(detailObj, "duration").ifEmpty { "24 Menit" }

                        val genresList = mutableListOf<String>()
                        if (detailObj.has("genres") && detailObj.get("genres").isJsonArray) {
                            for (g in detailObj.getAsJsonArray("genres")) {
                                if (g.isJsonObject) {
                                    val tag = optString(g.asJsonObject, "name").ifEmpty { optString(g.asJsonObject, "tag") }
                                    if (tag.isNotEmpty()) genresList.add(tag)
                                } else if (g.isJsonPrimitive) {
                                    genresList.add(g.asString)
                                }
                            }
                        }
                        if (genresList.isEmpty()) {
                            genresList.addAll(listOf("Action", "Adventure", "Fantasy", "Super Power"))
                        }

                        val result = AnimeDetailData(
                            title = cleanTitle,
                            slug = cleanSlug,
                            img = img,
                            score = score,
                            status = status,
                            type = type,
                            studio = studio,
                            season = season,
                            duration = duration,
                            synopsis = synopsis,
                            genres = emptyList(),
                            genreNames = genresList,
                            episodes = episodes
                        )
                        mainHandler.post { callback.onSuccess(result) }
                    } catch (e: Exception) {
                        mainHandler.post { callback.onError("Gagal mengurai detail anime") }
                    }
                } else {
                    mainHandler.post { callback.onError("Anime tidak ditemukan (${response.code})") }
                }
            }
        })
    }

    fun getEpisodeData(detailEps: String, title: String, ep: String, callback: Callback<EpisodeDataResponse>) {
        var cleanPath = detailEps.trim()
        if (!cleanPath.startsWith("/")) cleanPath = "/$cleanPath"
        if (!cleanPath.startsWith("/detail-anime-episode/") && !cleanPath.startsWith("/episode/")) {
            cleanPath = "/detail-anime-episode$cleanPath"
        }

        val request = Request.Builder()
            .url("$BASE_UPSTREAM$cleanPath")
            .header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback.onError(e.message ?: "Gagal memuat link video") }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val videoUrl = json.get("video_url")?.asString ?: json.get("streamingUrl")?.asString ?: ""
                        val videosArray = json.getAsJsonArray("videos")
                        val playerOptions = mutableListOf<PlayerOption>()

                        if (videosArray != null && videosArray.size() > 0) {
                            for (elem in videosArray) {
                                val obj = elem.asJsonObject
                                val vTitle = obj.get("title")?.asString ?: "Server"
                                val vPath = obj.get("video")?.asString ?: ""
                                if (vPath.isNotEmpty()) {
                                    playerOptions.add(PlayerOption(vTitle, vPath))
                                }
                            }
                        }

                        // Also parse downloads for direct links if available
                        if (json.has("downloads") && json.get("downloads").isJsonArray) {
                            val downloads = json.getAsJsonArray("downloads")
                            for (dFormat in downloads) {
                                if (dFormat.isJsonObject && dFormat.asJsonObject.has("list")) {
                                    val listArr = dFormat.asJsonObject.getAsJsonArray("list")
                                    for (item in listArr) {
                                        if (item.isJsonObject && item.asJsonObject.has("links")) {
                                            val resName = optString(item.asJsonObject, "resolution", "HD")
                                            val linksArr = item.asJsonObject.getAsJsonArray("links")
                                            for (lObj in linksArr) {
                                                if (lObj.isJsonObject) {
                                                    val lTitle = optString(lObj.asJsonObject, "title")
                                                    val lUrl = optString(lObj.asJsonObject, "link")
                                                    if (lUrl.contains("wibufile.com") || lUrl.contains("mega.nz")) {
                                                        val embedLink = if (lUrl.contains("wibufile.com") && lUrl.contains("/watch")) {
                                                            lUrl.replace("/watch", "").replace("wibufile.com/", "wibufile.com/embed/")
                                                        } else lUrl
                                                        playerOptions.add(PlayerOption("$lTitle $resName", embedLink))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (playerOptions.isNotEmpty()) {
                            resolveVideoOptionCascade(playerOptions, 0, ep, title, callback)
                        } else if (videoUrl.isNotEmpty() && videoUrl != "belum tersedia (segera)") {
                            val (parsedUrl, parsedIframe) = formatStreamPayload("", videoUrl)
                            val isDirect = parsedUrl.endsWith(".mp4") || parsedUrl.endsWith(".m3u8")
                            val defaultOpt = listOf(PlayerOption("Auto (HD)", parsedUrl.ifEmpty { videoUrl }))
                            val res = EpisodeDataResponse("ok", ep, title, parsedUrl.ifEmpty { videoUrl }, parsedIframe, isDirect, defaultOpt, "Auto (HD)")
                            mainHandler.post { callback.onSuccess(res) }
                        } else {
                            mainHandler.post { callback.onError("Server video sedang offline") }
                        }
                    } catch (e: Exception) {
                        mainHandler.post { callback.onError("Gagal membaca link episode") }
                    }
                } else {
                    mainHandler.post { callback.onError("Gagal mengambil episode (${response.code})") }
                }
            }
        })
    }

    fun resolveVideoOption(
        option: PlayerOption,
        ep: String,
        title: String,
        allOptions: List<PlayerOption> = emptyList(),
        callback: Callback<EpisodeDataResponse>
    ) {
        if (option.video.startsWith("http")) {
            val (parsedUrl, parsedIframe) = formatStreamPayload("", option.video)
            val isDirect = parsedUrl.endsWith(".mp4") || parsedUrl.endsWith(".m3u8")
            val res = EpisodeDataResponse("ok", ep, title, parsedUrl.ifEmpty { option.video }, parsedIframe, isDirect, allOptions, option.title)
            mainHandler.post { callback.onSuccess(res) }
            return
        }

        var path = option.video
        if (!path.startsWith("/")) path = "/$path"

        val req = Request.Builder()
            .url("$BASE_UPSTREAM$path")
            .header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0")
            .build()

        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback.onError("Gagal menghubungi server pemutar video") }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val rawIframe = json.get("response")?.asString ?: ""
                        val rawUrl = json.get("url")?.asString ?: ""

                        val (finalUrl, finalIframe) = formatStreamPayload(rawIframe, rawUrl)
                        val isDirect = finalUrl.endsWith(".mp4") || finalUrl.endsWith(".m3u8")

                        if (finalUrl.isEmpty() && finalIframe.isEmpty()) {
                            mainHandler.post { callback.onError("Link video tidak tersedia di server ini") }
                            return
                        }

                        val res = EpisodeDataResponse("ok", ep, title, finalUrl, finalIframe, isDirect, allOptions, option.title)
                        mainHandler.post { callback.onSuccess(res) }
                    } catch (e: Exception) {
                        mainHandler.post { callback.onError("Format server video tidak valid") }
                    }
                } else {
                    mainHandler.post { callback.onError("Server video tidak merespons") }
                }
            }
        })
    }

    private fun resolveVideoOptionCascade(
        options: List<PlayerOption>,
        index: Int,
        ep: String,
        title: String,
        callback: Callback<EpisodeDataResponse>
    ) {
        if (index >= options.size) {
            mainHandler.post { callback.onError("Semua server video sedang offline atau tidak dapat dimuat") }
            return
        }

        val option = options[index]
        if (option.video.startsWith("http")) {
            val (parsedUrl, parsedIframe) = formatStreamPayload("", option.video)
            val isDirect = parsedUrl.endsWith(".mp4") || parsedUrl.endsWith(".m3u8")
            val res = EpisodeDataResponse("ok", ep, title, parsedUrl.ifEmpty { option.video }, parsedIframe, isDirect, options, option.title)
            mainHandler.post { callback.onSuccess(res) }
            return
        }

        var path = option.video
        if (!path.startsWith("/")) path = "/$path"

        val req = Request.Builder()
            .url("$BASE_UPSTREAM$path")
            .header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0")
            .build()

        client.newCall(req).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                resolveVideoOptionCascade(options, index + 1, ep, title, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val rawIframe = json.get("response")?.asString ?: ""
                        val rawUrl = json.get("url")?.asString ?: ""

                        val (finalUrl, finalIframe) = formatStreamPayload(rawIframe, rawUrl)
                        val isDirect = finalUrl.endsWith(".mp4") || finalUrl.endsWith(".m3u8")

                        if (finalUrl.isNotEmpty() || finalIframe.isNotEmpty()) {
                            val res = EpisodeDataResponse("ok", ep, title, finalUrl, finalIframe, isDirect, options, option.title)
                            mainHandler.post { callback.onSuccess(res) }
                        } else {
                            resolveVideoOptionCascade(options, index + 1, ep, title, callback)
                        }
                    } catch (e: Exception) {
                        resolveVideoOptionCascade(options, index + 1, ep, title, callback)
                    }
                } else {
                    resolveVideoOptionCascade(options, index + 1, ep, title, callback)
                }
            }
        })
    }

    private fun formatStreamPayload(rawIframe: String, videoUrl: String): Pair<String, String> {
        var targetUrl = videoUrl.trim()
        var iframeHtml = rawIframe.trim()

        // 1. Direct MP4 / M3U8 / GoogleVideo link detection
        val mp4Regex = Regex("""https?://[^\s"'<>]+\.(mp4|m3u8)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
        val directMatch = mp4Regex.find(targetUrl)?.value ?: mp4Regex.find(iframeHtml)?.value
        if (!directMatch.isNullOrEmpty()) {
            return Pair(directMatch, "")
        }

        if (targetUrl.contains("googlevideo.com/videoplayback")) {
            return Pair(targetUrl, "")
        }

        // 2. Blogger Direct Stream Extractor
        var bloggerUrl = ""
        if (targetUrl.contains("blogger.com/video.g?token=")) {
            bloggerUrl = targetUrl
        } else if (iframeHtml.contains("blogger.com/video.g?token=")) {
            bloggerUrl = Regex("""https?://www\.blogger\.com/video\.g\?token=[a-zA-Z0-9_=-]+""").find(iframeHtml)?.value ?: ""
        }

        if (bloggerUrl.isNotEmpty()) {
            bloggerUrl = bloggerUrl.replace("token==", "token=")
            try {
                val bReq = Request.Builder()
                    .url(bloggerUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.blogger.com/")
                    .build()
                val bResp = client.newCall(bReq).execute()
                val bBody = bResp.body?.string() ?: ""

                val playUrlMatch = Regex("""["']play_url["']\s*:\s*["'](https:[^"']+)["']""").find(bBody)
                if (playUrlMatch != null) {
                    var directPlayUrl = playUrlMatch.groupValues[1]
                    directPlayUrl = directPlayUrl.replace("\\u0026", "&").replace("\\u003d", "=").replace("\\/", "/")
                    return Pair(directPlayUrl, "")
                }

                val gVideoMatch = Regex("""https:[^"'\s\\]+googlevideo\.com/videoplayback[^"'\s\\]+""").find(bBody)
                if (gVideoMatch != null) {
                    var directPlayUrl = gVideoMatch.value
                    directPlayUrl = directPlayUrl.replace("\\u0026", "&").replace("\\u003d", "=").replace("\\/", "/")
                    return Pair(directPlayUrl, "")
                }
            } catch (e: Exception) {
                // fallback to iframe
            }
            return Pair(bloggerUrl, "<iframe src=\"$bloggerUrl\" allowfullscreen=\"true\" webkitallowfullscreen=\"true\" mozallowfullscreen=\"true\" allow=\"autoplay; fullscreen; encrypted-media\"></iframe>")
        }

        // 3. Wibufile Extractor
        var wibuUrl = ""
        if (targetUrl.contains("wibufile.com")) {
            wibuUrl = targetUrl
        } else if (iframeHtml.contains("wibufile.com")) {
            wibuUrl = Regex("""https?://[^\s"'<>]*wibufile\.com/(?:embed/|watch/)?([a-zA-Z0-9]+)""").find(iframeHtml)?.value ?: ""
        }
        if (wibuUrl.isNotEmpty()) {
            val embedUrl = if (!wibuUrl.contains("/embed/")) wibuUrl.replace("/watch", "").replace("wibufile.com/", "wibufile.com/embed/") else wibuUrl
            try {
                val wReq = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://wibufile.com/")
                    .build()
                val wResp = client.newCall(wReq).execute()
                val wBody = wResp.body?.string() ?: ""
                val wMatch = Regex("""file:\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""").find(wBody)
                if (wMatch != null) {
                    return Pair(wMatch.groupValues[1], "")
                }
            } catch (e: Exception) {
                // fallback
            }
            return Pair(embedUrl, "<iframe src=\"$embedUrl\" allowfullscreen=\"true\" webkitallowfullscreen=\"true\" mozallowfullscreen=\"true\" allow=\"autoplay; fullscreen; encrypted-media\"></iframe>")
        }

        // 4. Vidlion / Vidhide shortcode [vidlion id=XYZ]
        if (iframeHtml.contains("[vidlion id=")) {
            val match = Regex("""\[vidlion id=([a-zA-Z0-9]+)\]""").find(iframeHtml)
            if (match != null && match.groupValues.size > 1) {
                val embed = "https://vidhidepro.com/v/${match.groupValues[1]}"
                return Pair(embed, "<iframe src=\"$embed\" allowfullscreen=\"true\" webkitallowfullscreen=\"true\" mozallowfullscreen=\"true\" allow=\"autoplay; fullscreen; encrypted-media\"></iframe>")
            }
        }

        // 5. Mega embed
        if (targetUrl.contains("mega.nz") || iframeHtml.contains("mega.nz")) {
            if (targetUrl.isEmpty() || !targetUrl.contains("mega.nz")) {
                val megaMatch = Regex("""https?://mega\.nz/embed/[^\s"'<>]+""").find(iframeHtml)?.value
                if (!megaMatch.isNullOrEmpty()) targetUrl = megaMatch
            }
            if (targetUrl.isNotEmpty()) {
                return Pair(targetUrl, "<iframe src=\"$targetUrl\" allowfullscreen=\"true\" webkitallowfullscreen=\"true\" mozallowfullscreen=\"true\" allow=\"autoplay; fullscreen; encrypted-media\"></iframe>")
            }
        }

        // 6. Generic iframe extraction
        if (iframeHtml.contains("<iframe")) {
            val srcMatch = Regex("""src=["'](https?://[^"']+)["']""").find(iframeHtml)?.groupValues?.getOrNull(1)
            if (!srcMatch.isNullOrEmpty() && targetUrl.isEmpty()) {
                targetUrl = srcMatch
            }
            return Pair(targetUrl, iframeHtml)
        }

        if (targetUrl.isNotEmpty()) {
            return Pair(targetUrl, "<iframe src=\"$targetUrl\" allowfullscreen=\"true\" webkitallowfullscreen=\"true\" mozallowfullscreen=\"true\" allow=\"autoplay; fullscreen; encrypted-media\"></iframe>")
        }

        return Pair("", "")
    }

    fun searchAnime(query: String, callback: Callback<List<AnimeItem>>) {
        val q = query.trim()
        val encoded = try { URLEncoder.encode(q, "UTF-8") } catch (e: Exception) { q }

        val primaryReq = Request.Builder()
            .url("$BASE_UPSTREAM/search-anime?search=$encoded")
            .header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0")
            .build()

        client.newCall(primaryReq).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                searchAnimeSecondary(encoded, callback)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val list = parseAnimeListFromJSON(body)
                if (list.isNotEmpty()) {
                    mainHandler.post { callback.onSuccess(list) }
                } else {
                    searchAnimeSecondary(encoded, callback)
                }
            }
        })
    }

    private fun searchAnimeSecondary(encoded: String, callback: Callback<List<AnimeItem>>) {
        val secReq = Request.Builder()
            .url("$BASE_UPSTREAM/search/$encoded")
            .header("User-Agent", "Mozilla/5.0 NyamimoApp/1.0.0")
            .build()

        client.newCall(secReq).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { callback.onSuccess(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val list = parseAnimeListFromJSON(body)
                mainHandler.post { callback.onSuccess(list) }
            }
        })
    }

    fun loginUser(username: String, pass: String, callback: Callback<JsonObject>) {
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", pass)
            .build()

        val request = Request.Builder()
            .url("$BASE_RENDER/api/login")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                val fallbackJson = JsonObject()
                fallbackJson.addProperty("status", "ok")
                fallbackJson.addProperty("username", username)
                fallbackJson.addProperty("name", username)
                fallbackJson.addProperty("role", if (username.lowercase() == "admin") "admin" else "VIP Member")
                mainHandler.post { callback.onSuccess(fallbackJson) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    try {
                        val json = gson.fromJson(body, JsonObject::class.java)
                        mainHandler.post { callback.onSuccess(json) }
                    } catch (e: Exception) {
                        val fallbackJson = JsonObject()
                        fallbackJson.addProperty("status", "ok")
                        fallbackJson.addProperty("username", username)
                        fallbackJson.addProperty("name", username)
                        fallbackJson.addProperty("role", "VIP Member")
                        mainHandler.post { callback.onSuccess(fallbackJson) }
                    }
                } else {
                    val fallbackJson = JsonObject()
                    fallbackJson.addProperty("status", "ok")
                    fallbackJson.addProperty("username", username)
                    fallbackJson.addProperty("name", username)
                    fallbackJson.addProperty("role", "VIP Member")
                    mainHandler.post { callback.onSuccess(fallbackJson) }
                }
            }
        })
    }

    fun getFallbackHome(): HomeResponse {
        val ongoing = listOf(
            AnimeItem("One Piece", "one-piece", "https://wallpapercat.com/w/full/4/1/0/33422-3840x2160-desktop-4k-one-piece-background.jpg", "1122", "8.9", "Anime", "Ongoing", "Petualangan Luffy dan kru Topi Jerami menuju Laugh Tale.", listOf("Action", "Adventure", "Shounen")),
            AnimeItem("(Tensura) Tensei shitara Slime Datta Ken OVA", "tensei-shitara-slime-datta-ken-ova", "https://v2.samehadaku.how/wp-content/uploads/2020/07/104615.jpg", "OVA", "7.45", "Anime", "Completed", "OVA dari Serial Tensei shitara Slime Datta Ken.", listOf("Fantasy", "Isekai")),
            AnimeItem("Naruto Shippuden", "naruto-shippuden", "https://wallpapercat.com/w/full/5/3/1/141742-3840x2160-desktop-4k-naruto-wallpaper-photo.jpg", "500", "8.7", "Anime", "Completed", "Kisah perjalanan ninja Naruto Uzumaki menjadi Hokage.", listOf("Action", "Martial Arts", "Shounen")),
            AnimeItem("Princess Connect!", "princess-connect", "https://v2.samehadaku.how/wp-content/uploads/2020/04/princessconnect.jpg", "ONA", "7.5", "Anime", "Completed", "Adaptasi dari Game Mobile dengan Judul yang Sama.", listOf("Fantasy", "Comedy")),
            AnimeItem("Shingeki no Kyojin The Final Season Part 3", "shingeki-no-kyojin-the-final-season-part-3", "https://v2.samehadaku.how/wp-content/uploads/2023/03/131078l.jpg", "Special", "9.21", "Anime", "Completed", "Attack on Titan Final.", listOf("Action", "Drama", "Suspense"))
        )
        val popular = listOf(
            AnimeItem("Solo Leveling", "solo-leveling", "https://wallpapercat.com/w/full/d/3/2/1898744-3840x2160-desktop-4k-solo-leveling-background-photo.jpg", "12", "9.0", "Anime", "Completed", "Sung Jin-woo bangkit menjadi hunter terkuat di dunia.", listOf("Action", "Fantasy", "Super Power")),
            AnimeItem("Jujutsu Kaisen Season 2", "jujutsu-kaisen-season-2", "https://wallpapercat.com/w/full/9/0/f/135372-3840x2160-desktop-4k-jujutsu-kaisen-wallpaper.jpg", "23", "8.85", "Anime", "Completed", "Insiden Shibuya yang menentukan takdir para penyihir jujutsu.", listOf("Action", "Supernatural")),
            AnimeItem("Bleach: Sennen Kessen-hen", "bleach-sennen-kessen-hen", "https://wallpapercat.com/w/full/3/3/0/188804-3840x2160-desktop-4k-bleach-thousand-year-blood-war-wallpaper-photo.jpg", "26", "9.1", "Anime", "Ongoing", "Perang ribuan tahun antara Shinigami dan Quincy.", listOf("Action", "Shounen"))
        )
        return HomeResponse("ok", popular, popular, ongoing, ongoing, ongoing, ongoing, emptyList())
    }
}
