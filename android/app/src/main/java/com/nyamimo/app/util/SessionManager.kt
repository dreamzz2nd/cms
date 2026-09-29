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

    fun addWatchHistory(context: Context, anime: AnimeItem) {
        val list = getWatchHistory(context).toMutableList()
        list.removeAll { it.slug == anime.slug || it.title.equals(anime.title, ignoreCase = true) }
        list.add(0, anime)
        val trimmed = if (list.size > 20) list.take(20) else list

        getPrefs(context).edit()
            .putString(KEY_WATCH_HISTORY, gson.toJson(trimmed))
            .apply()
    }

    fun getWatchHistory(context: Context): List<AnimeItem> {
        val json = getPrefs(context).getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AnimeItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
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
