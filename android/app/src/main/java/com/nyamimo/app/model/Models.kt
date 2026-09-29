package com.nyamimo.app.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class HomeResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("banners") val banners: List<AnimeItem> = emptyList(),
    @SerializedName("popular") val popular: List<AnimeItem> = emptyList(),
    @SerializedName("ongoing") val ongoing: List<AnimeItem> = emptyList(),
    @SerializedName("completed") val completed: List<AnimeItem> = emptyList(),
    @SerializedName("action") val action: List<AnimeItem> = emptyList(),
    @SerializedName("fantasy") val fantasy: List<AnimeItem> = emptyList(),
    @SerializedName("genres") val genres: List<GenreItem> = emptyList()
) : Serializable

data class AnimeItem(
    @SerializedName("title") val title: String = "",
    @SerializedName("slug") val slug: String = "",
    @SerializedName("img") val img: String = "",
    @SerializedName("episode") val episode: String = "",
    @SerializedName("score") val score: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("synopsis") val synopsis: String = "",
    @SerializedName("genres") val genres: List<String> = emptyList(),
    @SerializedName("watchDate") val watchDate: String = "",
    @SerializedName("watchProgressPercent") val watchProgressPercent: Int = 0,
    @SerializedName("watchDurationText") val watchDurationText: String = "",
    @SerializedName("timeGroup") val timeGroup: String = "Hari Ini",
    @SerializedName("isDownloaded") val isDownloaded: Boolean = false,
    @SerializedName("downloadPath") val downloadPath: String = "",
    @SerializedName("downloadSize") val downloadSize: String = ""
) : Serializable

data class GenreItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = ""
) : Serializable

data class AnimeDetailResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("data") val data: AnimeDetailData = AnimeDetailData()
) : Serializable

data class AnimeDetailData(
    @SerializedName("title") val title: String = "",
    @SerializedName("slug") val slug: String = "",
    @SerializedName("img") val img: String = "",
    @SerializedName("score") val score: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("type") val type: String = "TV Series",
    @SerializedName("studio") val studio: String = "",
    @SerializedName("season") val season: String = "",
    @SerializedName("duration") val duration: String = "",
    @SerializedName("synopsis") val synopsis: String = "",
    @SerializedName("genres") val genres: List<GenreItem> = emptyList(),
    @SerializedName("genreNames") val genreNames: List<String> = emptyList(),
    @SerializedName("episodes") val episodes: List<EpisodeItem> = emptyList(),
    @SerializedName("recommendations") val recommendations: List<AnimeItem> = emptyList()
) : Serializable

data class EpisodeItem(
    @SerializedName("title") val title: String = "",
    @SerializedName("number") val number: String = "",
    @SerializedName("episode") val episode: String = "",
    @SerializedName("link") val link: String = "",
    @SerializedName("detail_eps") val detailEps: String = ""
) : Serializable

data class EpisodeDataResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("episodeNum") val episodeNum: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("videoURL") val videoURL: String = "",
    @SerializedName("rawIframe") val rawIframe: String = "",
    @SerializedName("isDirectVideo") val isDirectVideo: Boolean = false,
    @SerializedName("videos") val videos: List<PlayerOption> = emptyList(),
    @SerializedName("activeServerTitle") val activeServerTitle: String = ""
) : Serializable

data class PlayerOption(
    @SerializedName("title") val title: String = "",
    @SerializedName("video") val video: String = ""
) : Serializable

data class SearchResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("query") val query: String = "",
    @SerializedName("results") val results: List<AnimeItem> = emptyList()
) : Serializable
