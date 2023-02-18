package me.stageguard.osu.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
public data class BeatmapSetSearch(
    @SerialName("beatmapsets")
    val beatmapsets: List<BeatmapSet>,
    @SerialName("error")
    val error: String? = "",
    @SerialName("recommended_difficulty")
    val recommendedDifficulty: Double,
    @SerialName("search")
    val search: Search,
    @SerialName("total")
    val total: Int,
    @SerialName("cursor")
    val cursor: Cursor?
)

@Serializable
public data class Search(
    @SerialName("sort")
    val sort: String
)

@Serializable
public data class Cursor(
    @SerialName("approved_date")
    val approvedDate: String,
    @SerialName("_id")
    val id: String
)