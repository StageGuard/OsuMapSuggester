package me.stageguard.obms.osu.api.dto
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName


@Serializable
data class BeatmapSetSearchDTO(
    @SerialName("beatmapsets")
    val beatmapsets: List<BeatmapsetDTO>,
    @SerialName("error")
    val error: String? = "",
    @SerialName("recommended_difficulty")
    val recommendedDifficulty: Double,
    @SerialName("search")
    val search: Search,
    @SerialName("total")
    val total: Int,
    @SerialName("cursor")
    val cursor: CursorDTO?
)

@Serializable
data class Search(
    @SerialName("sort")
    val sort: String
)

@Serializable
data class CursorDTO(
    @SerialName("approved_date")
    val approvedDate: String,
    @SerialName("_id")
    val id: String
)