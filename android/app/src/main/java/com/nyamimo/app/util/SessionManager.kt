package com.nyamimo.app.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nyamimo.app.model.AnimeItem

data class UserSession(
    val username: String,
    val name: String,
    val email: String = "",
    val role: String = "user",
    val avatar: String = "",
    val isLoggedIn: Boolean = false
)

object SessionManager {
    private const val PREF_NAME = "nyamimo_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USERNAME = "username"
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_ROLE = "role"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_WATCH_HISTORY = "watch_history_json"
    private const val KEY_SEARCH_HISTORY = "search_history_json"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private const val KEY_DOWNLOADS = "downloads_list_json"

    fun saveUser(context: Context, username: String, name: String, email: String, role: String, avatar: String) {
        val editor = getPrefs(context).edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_USERNAME, username)
        editor.putString(KEY_NAME, name)
        editor.putString(KEY_EMAIL, email)
        editor.putString(KEY_ROLE, role)
        editor.putString(KEY_AVATAR, avatar)
        editor.apply()
    }

    fun getUser(context: Context): UserSession {
        val prefs = getPrefs(context)
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val name = prefs.getString(KEY_NAME, if (isLoggedIn) username else "Tamu / Belum Masuk") ?: ""
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val role = prefs.getString(KEY_ROLE, if (isLoggedIn) "VIP Member" else "Tamu") ?: "Tamu"
        val avatar = prefs.getString(KEY_AVATAR, "") ?: ""

        return UserSession(username, name, email, role, avatar, isLoggedIn)
    }

    fun logout(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun addWatchHistory(context: Context, anime: AnimeItem, ep: String = "", progress: Int = 50, durationText: String = "18:24 / 24:00") {
        val list = getRawWatchHistory(context).toMutableList()
        list.removeAll { it.slug == anime.slug || it.title.equals(anime.title, ignoreCase = true) }
        
        val now = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val updated = anime.copy(
            episode = if (ep.isNotEmpty()) ep else (if (anime.episode.isNotEmpty()) anime.episode else "1"),
            watchDate = now,
            watchProgressPercent = if (progress in 5..100) progress else 65,
            watchDurationText = durationText,
            timeGroup = "Hari Ini"
        )
        list.add(0, updated)
        val trimmed = if (list.size > 30) list.take(30) else list

        getPrefs(context).edit()
            .putString(KEY_WATCH_HISTORY, gson.toJson(trimmed))
            .apply()
    }

    private fun getRawWatchHistory(context: Context): List<AnimeItem> {
        val json = getPrefs(context).getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AnimeItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getWatchHistory(context: Context): List<AnimeItem> {
        val raw = getRawWatchHistory(context)
        if (raw.isNotEmpty()) return raw

        // Default initial items matching user's requested layout screenshot
        val defaultHistory = listOf(
            AnimeItem(
                title = "Black Clover",
                slug = "black-clover",
                img = "https://cdn.myanimelist.net/images/anime/2/88339l.jpg",
                episode = "170",
                watchDate = "28/09/2026",
                watchProgressPercent = 85,
                watchDurationText = "21:15 / 24:00",
                timeGroup = "Kemarin"
            ),
            AnimeItem(
                title = "Trapped in a Dating Sim 2: The World of Otome Games is Tough for Mobs",
                slug = "trapped-in-a-dating-sim-2",
                img = "https://cdn.myanimelist.net/images/anime/1108/131078l.jpg",
                episode = "1",
                watchDate = "27/09/2026",
                watchProgressPercent = 45,
                watchDurationText = "10:45 / 23:40",
                timeGroup = "Sebelumnya"
            ),
            AnimeItem(
                title = "Though I Am an Inept Villainess",
                slug = "though-i-am-an-inept-villainess",
                img = "https://cdn.myanimelist.net/images/anime/1761/141014l.jpg",
                episode = "1",
                watchDate = "27/09/2026",
                watchProgressPercent = 90,
                watchDurationText = "22:00 / 24:10",
                timeGroup = "Sebelumnya"
            ),
            AnimeItem(
                title = "Boku no Hero Academia S4",
                slug = "boku-no-hero-academia-s4",
                img = "https://cdn.myanimelist.net/images/anime/1412/107931l.jpg",
                episode = "1",
                watchDate = "27/09/2026",
                watchProgressPercent = 60,
                watchDurationText = "14:20 / 23:55",
                timeGroup = "Sebelumnya"
            ),
            AnimeItem(
                title = "Ghost in the Cell (2025) 18+ | Misteri & Teror Mencekam - 1080P",
                slug = "ghost-in-the-cell",
                img = "https://cdn.myanimelist.net/images/anime/1171/109222l.jpg",
                episode = "Movie",
                watchDate = "27/09/2026",
                watchProgressPercent = 35,
                watchDurationText = "1:46:11",
                type = "Movie",
                timeGroup = "Sebelumnya"
            ),
            AnimeItem(
                title = "Re:ZERO -Starting Life in Another World- Season 4",
                slug = "rezero-season-4",
                img = "https://cdn.myanimelist.net/images/anime/1522/128086l.jpg",
                episode = "12",
                watchDate = "19/09/2026",
                watchProgressPercent = 100,
                watchDurationText = "24:30 / 24:30",
                timeGroup = "Sebelumnya"
            ),
            AnimeItem(
                title = "BLEACH: Sennen Kessen-hen - Soukoku-tan",
                slug = "bleach-thousand-year-blood-war",
                img = "https://cdn.myanimelist.net/images/anime/1908/135431l.jpg",
                episode = "14",
                watchDate = "18/09/2026",
                watchProgressPercent = 75,
                watchDurationText = "18:00 / 24:00",
                timeGroup = "Sebelumnya"
            )
        )
        getPrefs(context).edit().putString(KEY_WATCH_HISTORY, gson.toJson(defaultHistory)).apply()
        return defaultHistory
    }

    fun removeWatchHistory(context: Context, slug: String) {
        val list = getRawWatchHistory(context).toMutableList()
        list.removeAll { it.slug == slug }
        getPrefs(context).edit().putString(KEY_WATCH_HISTORY, gson.toJson(list)).apply()
    }

    fun clearWatchHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_WATCH_HISTORY).apply()
    }

    // --- DOWNLOADS PERSISTENCE ---
    fun addDownload(context: Context, anime: AnimeItem, ep: String = "1", size: String = "185 MB", path: String = "") {
        val list = getDownloads(context).toMutableList()
        val targetEp = if (ep.isNotEmpty()) ep else (if (anime.episode.isNotEmpty()) anime.episode else "1")
        list.removeAll { it.slug == anime.slug && it.episode == targetEp }

        val now = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val downloadedItem = anime.copy(
            episode = targetEp,
            isDownloaded = true,
            downloadSize = size,
            downloadPath = path.ifEmpty { "/sdcard/Nyamimo/Downloads/${anime.slug}_ep$targetEp.mp4" },
            watchDate = now
        )
        list.add(0, downloadedItem)
        getPrefs(context).edit().putString(KEY_DOWNLOADS, gson.toJson(list)).apply()
    }

    fun getDownloads(context: Context): List<AnimeItem> {
        val json = getPrefs(context).getString(KEY_DOWNLOADS, null)
        if (!json.isNullOrEmpty()) {
            return try {
                val type = object : TypeToken<List<AnimeItem>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        // Default initial downloads
        val defaultDownloads = listOf(
            AnimeItem(
                title = "Black Clover",
                slug = "black-clover",
                img = "https://cdn.myanimelist.net/images/anime/2/88339l.jpg",
                episode = "170",
                isDownloaded = true,
                downloadSize = "195 MB",
                downloadPath = "/sdcard/Nyamimo/Downloads/black-clover_ep170.mp4",
                watchDate = "28/09/2026"
            ),
            AnimeItem(
                title = "Boku no Hero Academia S4",
                slug = "boku-no-hero-academia-s4",
                img = "https://cdn.myanimelist.net/images/anime/1412/107931l.jpg",
                episode = "1",
                isDownloaded = true,
                downloadSize = "182 MB",
                downloadPath = "/sdcard/Nyamimo/Downloads/mha_s4_ep1.mp4",
                watchDate = "27/09/2026"
            )
        )
        getPrefs(context).edit().putString(KEY_DOWNLOADS, gson.toJson(defaultDownloads)).apply()
        return defaultDownloads
    }

    fun removeDownload(context: Context, slug: String, ep: String = "") {
        val list = getDownloads(context).toMutableList()
        if (ep.isNotEmpty()) {
            list.removeAll { it.slug == slug && it.episode == ep }
        } else {
            list.removeAll { it.slug == slug }
        }
        getPrefs(context).edit().putString(KEY_DOWNLOADS, gson.toJson(list)).apply()
    }

    fun addSearchQuery(context: Context, query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val list = getSearchHistory(context).toMutableList()
        list.removeAll { it.equals(q, ignoreCase = true) }
        list.add(0, q)
        val trimmed = if (list.size > 15) list.take(15) else list
        getPrefs(context).edit().putString(KEY_SEARCH_HISTORY, gson.toJson(trimmed)).apply()
    }

    fun getSearchHistory(context: Context): List<String> {
        val json = getPrefs(context).getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearSearchHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_SEARCH_HISTORY).apply()
    }
}
