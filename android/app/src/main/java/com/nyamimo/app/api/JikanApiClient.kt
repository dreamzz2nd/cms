package com.nyamimo.app.api

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nyamimo.app.model.MimoNewsItem
import okhttp3.*
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AnimeMalStats(
    val members: Int = 0,
    val favorites: Int = 0,
    val scoredBy: Int = 0,
    val score: String = "N/A",
    val rank: Int = 0,
    val popularity: Int = 0
)

/**
 * Primary data source: Jikan API (https://api.jikan.moe) — official MAL REST wrapper.
 * Automatic fallback: AniListApiClient when Jikan is down / returns empty.
 */
object JikanApiClient {


    private const val BASE_JIKAN = "https://api.jikan.moe/v4"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cache = mutableMapOf<String, List<MimoNewsItem>>()

    fun clearCache() {
        cache.clear()
    }

    // ─── Parsing ───────────────────────────────────────────────────────────────

    private fun optString(obj: JsonObject?, key: String, fallback: String = ""): String {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull) return fallback
        return try {
            val elem = obj.get(key)
            if (elem.isJsonPrimitive) elem.asString else elem.toString().trim('"')
        } catch (e: Exception) { fallback }
    }

    fun formatCount(count: Int): String = when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000     -> String.format(Locale.US, "%.1fK", count / 1000.0)
        else               -> "$count"
    }

    private fun parseJikanAnimeList(jsonBody: String): List<MimoNewsItem> {
        val list = mutableListOf<MimoNewsItem>()
        try {
            val json = gson.fromJson(jsonBody, JsonObject::class.java)
            // If Jikan returned an error status (e.g. 504), data array won't exist
            if (json.has("status") && json.get("status").asInt >= 400) return list
            val dataArray = json.getAsJsonArray("data") ?: return list

            for (elem in dataArray) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject

                val malId = obj.get("mal_id")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val title = optString(obj, "title", "Anime")
                val titleJapanese = optString(obj, "title_japanese", "")
                val titleEnglish  = optString(obj, "title_english", "")

                var imgUrl = ""
                obj.getAsJsonObject("images")?.let { imgObj ->
                    imgObj.getAsJsonObject("jpg")?.let { jpg ->
                        imgUrl = optString(jpg, "large_image_url").ifEmpty { optString(jpg, "image_url") }
                    }
                    if (imgUrl.isEmpty()) {
                        imgObj.getAsJsonObject("webp")?.let { webp ->
                            imgUrl = optString(webp, "large_image_url").ifEmpty { optString(webp, "image_url") }
                        }
                    }
                }

                var releaseDate = ""
                obj.getAsJsonObject("aired")?.let {
                    releaseDate = optString(it, "string", "")
                }
                if (releaseDate.isEmpty()) {
                    val season = optString(obj, "season", "").replaceFirstChar { it.uppercase() }
                    val year   = optString(obj, "year", "")
                    releaseDate = if (season.isNotEmpty() && year.isNotEmpty()) "$season $year" else "Segera Rilis"
                }

                val seasonStr = optString(obj, "season", "").replaceFirstChar { it.uppercase() }
                val yearStr   = optString(obj, "year", "")
                val seasonYear = if (seasonStr.isNotEmpty() && yearStr.isNotEmpty()) "$seasonStr $yearStr" else releaseDate

                val eps      = optString(obj, "episodes", "").let { if (it.isEmpty() || it == "null") "? eps" else "$it eps" }
                val duration = optString(obj, "duration", "")
                val score    = optString(obj, "score", "").let { if (it.isEmpty() || it == "null") "N/A" else it }
                val synopsis = optString(obj, "synopsis", "Sinopsis belum tersedia.")

                var studioName = "Unknown Studio"
                obj.getAsJsonArray("studios")?.let { arr ->
                    if (arr.size() > 0 && arr[0].isJsonObject) {
                        studioName = optString(arr[0].asJsonObject, "name", "Studio")
                    }
                }

                val source = optString(obj, "source", "Manga")
                val type   = optString(obj, "type", "TV")
                val status = optString(obj, "status", "Upcoming")

                val genresList = mutableListOf<String>()
                obj.getAsJsonArray("genres")?.forEach { g ->
                    if (g.isJsonObject) {
                        val n = optString(g.asJsonObject, "name")
                        if (n.isNotEmpty()) genresList.add(n)
                    }
                }

                val membersCount = obj.get("members")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val favoritesCount = obj.get("favorites")?.takeIf { !it.isJsonNull }?.asInt ?: 0

                var trailerUrl  = ""
                var trailerEmbed = ""
                obj.getAsJsonObject("trailer")?.let { tr ->
                    trailerUrl   = optString(tr, "url", "")
                    trailerEmbed = optString(tr, "embed_url", "")
                }

                list.add(MimoNewsItem(
                    malId          = malId,
                    title          = title,
                    titleJapanese  = titleJapanese,
                    titleEnglish   = titleEnglish,
                    img            = imgUrl,
                    releaseDate    = releaseDate,
                    seasonYear     = seasonYear,
                    episodes       = eps,
                    duration       = duration,
                    score          = score,
                    synopsis       = synopsis,
                    studio         = studioName,
                    source         = source,
                    type           = type,
                    genres         = genresList,
                    members        = formatCount(membersCount),
                    favorites      = formatCount(favoritesCount),
                    trailerUrl     = trailerUrl,
                    trailerEmbedUrl = trailerEmbed,
                    status         = status
                ))
            }
        } catch (e: Exception) { /* ignore */ }
        return list
    }


    // ─── Public API (with automatic AniList fallback) ──────────────────────────

    fun getSeasonNow(onSuccess: (List<MimoNewsItem>) -> Unit, onError: () -> Unit) {
        val cacheKey = "season_now"
        cache[cacheKey]?.let { onSuccess(it); return }

        fetchJikan("$BASE_JIKAN/seasons/now?limit=40", cacheKey,
            onJikanSuccess = onSuccess,
            onJikanFail = {
                AniListApiClient.getSeasonNow(
                    onSuccess = { items -> cache[cacheKey] = items; onSuccess(items) },
                    onError   = onError
                )
            }
        )
    }

    fun getSeasonUpcoming(onSuccess: (List<MimoNewsItem>) -> Unit, onError: () -> Unit) {
        val cacheKey = "season_upcoming"
        cache[cacheKey]?.let { onSuccess(it); return }

        fetchJikan("$BASE_JIKAN/seasons/upcoming?limit=40", cacheKey,
            onJikanSuccess = onSuccess,
            onJikanFail = {
                AniListApiClient.getSeasonUpcoming(
                    onSuccess = { items -> cache[cacheKey] = items; onSuccess(items) },
                    onError   = onError
                )
            }
        )
    }

    fun getSeasonByYear(year: Int, season: String, onSuccess: (List<MimoNewsItem>) -> Unit, onError: () -> Unit) {
        val cacheKey = "season_${year}_$season"
        cache[cacheKey]?.let { onSuccess(it); return }

        fetchJikan("$BASE_JIKAN/seasons/$year/${season.lowercase()}?limit=40", cacheKey,
            onJikanSuccess = onSuccess,
            onJikanFail = {
                AniListApiClient.getSeasonByYear(year, season,
                    onSuccess = { items -> cache[cacheKey] = items; onSuccess(items) },
                    onError   = onError
                )
            }
        )
    }

    fun getSchedules(onSuccess: (List<MimoNewsItem>) -> Unit, onError: () -> Unit) {
        val cacheKey = "schedules"
        cache[cacheKey]?.let { onSuccess(it); return }

        fetchJikan("$BASE_JIKAN/schedules?limit=40", cacheKey,
            onJikanSuccess = onSuccess,
            onJikanFail = {
                AniListApiClient.getSchedules(
                    onSuccess = { items -> cache[cacheKey] = items; onSuccess(items) },
                    onError   = onError
                )
            }
        )
    }

    fun getTrendingTags(onSuccess: (List<com.nyamimo.app.adapter.TrendingTag>) -> Unit, onError: () -> Unit) {
        val request = Request.Builder()
            .url("$BASE_JIKAN/top/anime?filter=bypopularity&limit=10")
            .header("User-Agent", "NyamimoApp/1.3.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                AniListApiClient.getTrendingTags(onSuccess, onError)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val tags = parseTrendingTagsFromJikan(body)
                if (tags.isNotEmpty()) {
                    mainHandler.post { onSuccess(tags) }
                } else {
                    AniListApiClient.getTrendingTags(onSuccess, onError)
                }
            }
        })
    }


    private fun parseTrendingTagsFromJikan(jsonBody: String): List<com.nyamimo.app.adapter.TrendingTag> {

        val tags = mutableListOf<com.nyamimo.app.adapter.TrendingTag>()
        try {
            val json = gson.fromJson(jsonBody, JsonObject::class.java)
            if (json.has("status") && json.get("status").asInt >= 400) return tags
            val dataArray = json.getAsJsonArray("data") ?: return tags

            for (i in 0 until dataArray.size()) {
                val elem = dataArray[i]
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val title = optString(obj, "title").ifEmpty { optString(obj, "title_english") }
                if (title.isEmpty()) continue

                val (badge, badgeColor) = when (i) {
                    0, 1 -> Pair("TOP", "red")
                    2, 3, 4 -> Pair("PANAS", "orange")
                    5, 6, 7 -> Pair("BARU", "blue")
                    else -> Pair("", "")
                }
                tags.add(com.nyamimo.app.adapter.TrendingTag(title.lowercase(), badge, badgeColor))
            }
        } catch (e: Exception) { /* ignore */ }
        return tags
    }

    fun getAnimeStats(

        animeTitle: String,
        onSuccess: (AnimeMalStats) -> Unit,
        onError: () -> Unit
    ) {
        val cleanQuery = animeTitle
            .replace(Regex("(?i)season\\s*\\d+"), "")
            .replace(Regex("(?i)sub\\s*indo"), "")
            .replace(Regex("(?i)part\\s*\\d+"), "")
            .replace(Regex("(?i)batch"), "")
            .trim()

        val queryToUse = if (cleanQuery.isNotEmpty()) cleanQuery else animeTitle
        val encoded = java.net.URLEncoder.encode(queryToUse, "UTF-8")
        val url = "$BASE_JIKAN/anime?q=$encoded&limit=1"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NyamimoApp/1.3.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                AniListApiClient.getAnimeStats(queryToUse, onSuccess, onError)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val stats = parseAnimeStatsFromJikan(body)
                if (stats != null) {
                    mainHandler.post { onSuccess(stats) }
                } else {
                    AniListApiClient.getAnimeStats(queryToUse, onSuccess, onError)
                }
            }
        })
    }

    private fun parseAnimeStatsFromJikan(jsonBody: String): AnimeMalStats? {
        return try {
            val json = gson.fromJson(jsonBody, JsonObject::class.java)
            if (json.has("status") && json.get("status").asInt >= 400) return null
            val dataArray = json.getAsJsonArray("data") ?: return null
            if (dataArray.size() == 0 || !dataArray[0].isJsonObject) return null

            val obj = dataArray[0].asJsonObject
            val members = obj.get("members")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val favorites = obj.get("favorites")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val scoredBy = obj.get("scored_by")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val score = optString(obj, "score", "N/A")
            val rank = obj.get("rank")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val popularity = obj.get("popularity")?.takeIf { !it.isJsonNull }?.asInt ?: 0

            AnimeMalStats(members, favorites, scoredBy, score, rank, popularity)
        } catch (e: Exception) {
            null
        }
    }


    // ─── Internal ──────────────────────────────────────────────────────────────

    private fun fetchJikan(
        url: String,
        cacheKey: String,
        onJikanSuccess: (List<MimoNewsItem>) -> Unit,
        onJikanFail: () -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NyamimoApp/1.3.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Network failure → try AniList
                mainHandler.post { onJikanFail() }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val items = parseJikanAnimeList(body)
                if (items.isNotEmpty()) {
                    cache[cacheKey] = items
                    mainHandler.post { onJikanSuccess(items) }
                } else {
                    // Jikan returned empty/error → try AniList
                    mainHandler.post { onJikanFail() }
                }
            }
        })
    }
}

