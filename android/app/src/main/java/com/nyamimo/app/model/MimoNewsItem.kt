package com.nyamimo.app.model

import java.io.Serializable

data class MimoNewsItem(
    val malId: Int = 0,
    val title: String = "",
    val titleJapanese: String = "",
    val titleEnglish: String = "",
    val img: String = "",
    val releaseDate: String = "",
    val seasonYear: String = "",
    val episodes: String = "",
    val duration: String = "",
    val score: String = "N/A",
    val synopsis: String = "",
    val studio: String = "",
    val source: String = "",
    val type: String = "TV",
    val genres: List<String> = emptyList(),
    val members: String = "",
    val favorites: String = "",
    val trailerUrl: String = "",
    val trailerEmbedUrl: String = "",
    val status: String = "Upcoming"
) : Serializable

