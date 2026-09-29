package com.nyamimo.app.api

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nyamimo.app.model.MimoNewsItem
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * AniList GraphQL API client — used as fallback when Jikan/MAL is unavailable.
 * Data source: https://anilist.co (mirrors MAL data, very reliable, no API key needed)
 */
object AniListApiClient {

    private const val ANILIST_URL = "https://graphql.anilist.co"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ─── Season Detection ──────────────────────────────────────────────────────

    fun getCurrentSeason(): Pair<String, Int> {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val season = when (month) {
            12, 1, 2 -> "WINTER"
            3, 4, 5  -> "SPRING"
            6, 7, 8  -> "SUMMER"
            else     -> "FALL"
        }
        return Pair(season, year)
    }

    fun getNextSeason(): Pair<String, Int> {
        val (current, year) = getCurrentSeason()
        return when (current) {
            "WINTER" -> Pair("SPRING", year)
            "SPRING" -> Pair("SUMMER", year)
            "SUMMER" -> Pair("FALL", year)
            else     -> Pair("WINTER", year + 1)
        }
    }

    // ─── GraphQL Query Builders ────────────────────────────────────────────────

    private fun seasonalQuery(season: String, year: Int): String {
        val gql = """
            {
              Page(page: 1, perPage: 40) {
                media(season: $season, seasonYear: $year, type: ANIME, sort: POPULARITY_DESC) {
                  id
                  title { romaji english native }
                  coverImage { extraLarge large }
                  startDate { year month day }
                  genres
                  averageScore
                  description(asHtml: false)
                  episodes
                  duration
                  status
                  studios(isMain: true) { nodes { name } }
                  source
                  popularity
                  favourites
                  trailer { id site }
                  format
                }
              }
            }
        """.trimIndent()
        return gson.toJson(mapOf("query" to gql))
    }

    private fun releasingQuery(): String {
        val gql = """
            {
              Page(page: 1, perPage: 40) {
                media(status: RELEASING, type: ANIME, sort: POPULARITY_DESC) {
                  id
                  title { romaji english native }
                  coverImage { extraLarge large }
                  startDate { year month day }
                  genres
                  averageScore
                  description(asHtml: false)
                  episodes
                  duration
                  status
                  studios(isMain: true) { nodes { name } }
                  source
                  popularity
                  favourites
                  trailer { id site }
                  format
                }
              }
            }
        """.trimIndent()
        return gson.toJson(mapOf("query" to gql))
    }

    private fun trendingTagsQuery(): String {
        val gql = """
            {
              Page(page: 1, perPage: 10) {
                media(sort: TRENDING_DESC, type: ANIME) {
                  title { romaji english }
                }
              }
            }
        """.trimIndent()
        return gson.toJson(mapOf("query" to gql))
    }

    // ─── Parsing ───────────────────────────────────────────────────────────────

    private fun stripHtml(html: String): String = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#039;", "'")
        .trim()

    private fun mapSource(raw: String?): String = when (raw?.uppercase()) {
        "LIGHT_NOVEL" -> "Light Novel"
        "WEB_NOVEL"   -> "Web Novel"
        "VISUAL_NOVEL" -> "Visual Novel"
        "VIDEO_GAME"  -> "Video Game"
        "MANGA"       -> "Manga"
        "NOVEL"       -> "Novel"
        "ORIGINAL"    -> "Original"
        "MUSIC"       -> "Music"
        else          -> raw?.replaceFirstChar { it.uppercase() } ?: "Manga"
    }

    private fun mapStatus(raw: String?): String = when (raw?.uppercase()) {
        "RELEASING"       -> "Airing"
        "FINISHED"        -> "Completed"
        "NOT_YET_RELEASED" -> "Upcoming"
        "CANCELLED"       -> "Cancelled"
        else              -> "Upcoming"
    }

    private fun parseResponse(body: String, seasonLabel: String, year: Int): List<MimoNewsItem> {
        val list = mutableListOf<MimoNewsItem>()
        try {
            val root = gson.fromJson(body, JsonObject::class.java)
            val mediaArray = root
                ?.getAsJsonObject("data")
                ?.getAsJsonObject("Page")
                ?.getAsJsonArray("media") ?: return list

            val MONTH_NAMES = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
                                      "Jul","Aug","Sep","Oct","Nov","Dec")

            for (elem in mediaArray) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject

                val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asInt ?: 0

                // Titles
                val titleObj = obj.getAsJsonObject("title")
                val title      = titleObj?.get("romaji") ?.takeIf { !it.isJsonNull }?.asString ?: "Anime"
                val titleJp    = titleObj?.get("native") ?.takeIf { !it.isJsonNull }?.asString ?: ""
                val titleEn    = titleObj?.get("english")?.takeIf { !it.isJsonNull }?.asString ?: ""

                // Cover image
                var imgUrl = ""
                obj.getAsJsonObject("coverImage")?.let { img ->
                    imgUrl = img.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                          ?: img.get("large")    ?.takeIf { !it.isJsonNull }?.asString ?: ""
                }

                // Release date
                val seasonYear = "${seasonLabel.replaceFirstChar { it.uppercase() }} $year"
                var releaseDate = seasonYear
                obj.getAsJsonObject("startDate")?.let { d ->
                    val dy = d.get("year") ?.takeIf { !it.isJsonNull }?.asInt
                    val dm = d.get("month")?.takeIf { !it.isJsonNull }?.asInt
                    val dd = d.get("day")  ?.takeIf { !it.isJsonNull }?.asInt
                    if (dy != null && dm != null && dm in 1..12) {
                        releaseDate = if (dd != null) "$dd ${MONTH_NAMES[dm-1]} $dy"
                                      else "${MONTH_NAMES[dm-1]} $dy"
                    }
                }

                // Episodes & duration
                val eps = obj.get("episodes")?.takeIf { !it.isJsonNull }?.asInt
                val epsStr = if (eps != null) "$eps eps" else "? eps"
                val dur = obj.get("duration")?.takeIf { !it.isJsonNull }?.asInt
                val durStr = if (dur != null) "$dur min" else ""

                // Score: AniList 0-100 → display as X.X/10
                val scoreRaw = obj.get("averageScore")?.takeIf { !it.isJsonNull }?.asInt
                val scoreStr = if (scoreRaw != null) String.format(Locale.US, "%.1f", scoreRaw / 10.0) else "N/A"

                // Synopsis (strip HTML)
                val rawDesc = obj.get("description")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val synopsis = if (rawDesc.isNotEmpty()) stripHtml(rawDesc) else "Sinopsis belum tersedia."

                // Studio
                var studio = "Unknown Studio"
                obj.getAsJsonObject("studios")?.getAsJsonArray("nodes")?.let { nodes ->
                    if (nodes.size() > 0 && nodes[0].isJsonObject) {
                        studio = nodes[0].asJsonObject.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "Unknown Studio"
                    }
                }

                // Source, type, status
                val source = mapSource(obj.get("source")?.takeIf { !it.isJsonNull }?.asString)
                val type   = obj.get("format")?.takeIf { !it.isJsonNull }?.asString ?: "TV"
                val status = mapStatus(obj.get("status")?.takeIf { !it.isJsonNull }?.asString)

                // Genres
                val genres = mutableListOf<String>()
                obj.getAsJsonArray("genres")?.forEach { g ->
                    if (!g.isJsonNull) genres.add(g.asString)
                }

                // Popularity (Members) & Favourites (Likes)
                val pop = obj.get("popularity")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val membersStr = when {
                    pop >= 1_000_000 -> String.format(Locale.US, "%.1fM", pop / 1_000_000.0)
                    pop >= 1_000     -> String.format(Locale.US, "%.1fK", pop / 1000.0)
                    else             -> "$pop"
                }

                val favs = obj.get("favourites")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val favsStr = when {
                    favs >= 1_000_000 -> String.format(Locale.US, "%.1fM", favs / 1_000_000.0)
                    favs >= 1_000     -> String.format(Locale.US, "%.1fK", favs / 1000.0)
                    else              -> "$favs"
                }

                // Trailer (YouTube)
                var trailerUrl = ""
                obj.getAsJsonObject("trailer")?.let { tr ->
                    val trId   = tr.get("id")  ?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val trSite = tr.get("site") ?.takeIf { !it.isJsonNull }?.asString ?: ""
                    if (trSite.equals("youtube", ignoreCase = true) && trId.isNotEmpty()) {
                        trailerUrl = "https://www.youtube.com/watch?v=$trId"
                    }
                }

                list.add(MimoNewsItem(
                    malId          = id,
                    title          = title,
                    titleJapanese  = titleJp,
                    titleEnglish   = titleEn,
                    img            = imgUrl,
                    releaseDate    = releaseDate,
                    seasonYear     = seasonYear,
                    episodes       = epsStr,
                    duration       = durStr,
                    score          = scoreStr,
                    synopsis       = synopsis,
                    studio         = studio,
                    source         = source,
                    type           = type,
                    genres         = genres,
                    members        = membersStr,
                    favorites      = favsStr,
                    trailerUrl     = trailerUrl,
                    trailerEmbedUrl = "",
                    status         = status
                ))
            }
        } catch (e: Exception) { /* ignore */ }
        return list
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    fun getSeasonNow(onSuccess: (List<MimoNewsItem>) -> Unit, onError: () -> Unit) {
        val (season, year) = getCurrentSeason()
        post(seasonalQuery(season, year), season, year, onSuccess, onError)
    }

    fun getSeasonUpcoming(onSuccess: (List<MimoNewsItem>) -> Unit, onError: () -> Unit) {
        val (season, year) = getNextSeason()
        post(seasonalQuery(season, year), season, year, onSuccess, onError)
    }

    fun getSeasonByYear(year: Int, season: String, onSuccess: (List<MimoNewsItem>) -> Unit, onError: () -> Unit) {
        val s = season.uppercase()
        post(seasonalQuery(s, year), s, year, onSuccess, onError)
    }

    fun getSchedules(onSuccess: (List<MimoNewsItem>) -> Unit, onError: () -> Unit) {
        val (season, year) = getCurrentSeason()
        post(releasingQuery(), season, year, onSuccess, onError)
    }

    fun getTrendingTags(onSuccess: (List<com.nyamimo.app.adapter.TrendingTag>) -> Unit, onError: () -> Unit) {
        val body = trendingTagsQuery().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(ANILIST_URL)
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onError() }
            }
            override fun onResponse(call: Call, response: Response) {
                val respBody = response.body?.string() ?: ""
                val tags = parseTrendingTags(respBody)
                if (tags.isNotEmpty()) mainHandler.post { onSuccess(tags) }
                else mainHandler.post { onError() }
            }
        })
    }

    private fun parseTrendingTags(body: String): List<com.nyamimo.app.adapter.TrendingTag> {
        val tags = mutableListOf<com.nyamimo.app.adapter.TrendingTag>()
        try {
            val root = gson.fromJson(body, JsonObject::class.java)
            val mediaArray = root
                ?.getAsJsonObject("data")
                ?.getAsJsonObject("Page")
                ?.getAsJsonArray("media") ?: return tags

            for (i in 0 until mediaArray.size()) {
                val elem = mediaArray[i]
                if (!elem.isJsonObject) continue
                val titleObj = elem.asJsonObject.getAsJsonObject("title")
                val title = titleObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
                    ?: titleObj?.get("english")?.takeIf { !it.isJsonNull }?.asString ?: ""
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

    // ─── Internal HTTP ─────────────────────────────────────────────────────────

    private fun post(
        queryBody: String,
        season: String,
        year: Int,
        onSuccess: (List<MimoNewsItem>) -> Unit,
        onError: () -> Unit
    ) {
        val body = queryBody.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(ANILIST_URL)
            .post(body)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onError() }
            }
            override fun onResponse(call: Call, response: Response) {
                val respBody = response.body?.string() ?: ""
                val items = parseResponse(respBody, season, year)
                if (items.isNotEmpty()) mainHandler.post { onSuccess(items) }
                else mainHandler.post { onError() }
            }
        })
    }
}
