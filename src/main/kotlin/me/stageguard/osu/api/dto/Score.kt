package me.stageguard.osu.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import me.stageguard.obms.utils.CustomLocalDateTime

@Serializable
public data class BeatmapUserScore(
    @SerialName("position")
    val position: Int,
    @SerialName("score")
    val score: Score
)

@Serializable
public data class Score(
    @SerialName("accuracy")
    val accuracy: Double,
    @SerialName("beatmap")
    val beatmap: Beatmap? = null,
    @SerialName("beatmapset")
    val beatmapset: BeatmapSet? = null,
    @SerialName("best_id")
    val bestId: Long? = null,
    @SerialName("created_at")
    val createdAt: CustomLocalDateTime,
    @SerialName("id")
    val id: Long,
    @SerialName("max_combo")
    val maxCombo: Int,
    @SerialName("mode")
    val mode: String,
    @SerialName("mode_int")
    val modeInt: Int,
    @SerialName("mods")
    val mods: List<String>,
    @SerialName("passed")
    val passed: Boolean,
    @SerialName("perfect")
    val perfect: Boolean,
    @SerialName("pp")
    val pp: Double? = null,
    @SerialName("rank")
    val rank: String,
    @SerialName("rank_global")
    val rankGlobal: Int? = null,
    @SerialName("rank_country")
    val rankCountry: Int? = null,
    @SerialName("replay")
    val replay: Boolean? = null,
    @SerialName("score")
    val score: Long,
    @SerialName("statistics")
    val statistics: ScoreStatisticsDTO,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("weight")
    val weight: Weight? = null,
    @SerialName("user")
    val user: ScoreUser? = null
    /*@SerialName("match")
    val match: String? = null*/
)

@Serializable
public data class ScoreUser(
    @SerialName("avatar_url")
    val avatarUrl: String,
    @SerialName("country_code")
    val countryCode: String,
    @SerialName("id")
    val id: Int,
    @SerialName("last_visit")
    val lastVisit: CustomLocalDateTime? = null,
    @SerialName("username")
    val username: String
)

@Serializable
public data class Weight(
    val percentage: Double,
    val pp: Double
)

@Serializable
public data class Beatmap(
    @SerialName("accuracy")
    val accuracy: Double,
    @SerialName("ar")
    val ar: Double,
    @SerialName("beatmapset_id")
    val beatmapsetId: Int,
    @SerialName("bpm")
    val bpm: Double,
    @SerialName("convert")
    val convert: Boolean,
    @SerialName("count_circles")
    val countCircles: Int,
    @SerialName("count_sliders")
    val countSliders: Int,
    @SerialName("count_spinners")
    val countSpinners: Int,
    @SerialName("cs")
    val cs: Double,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    @SerialName("difficulty_rating")
    val difficultyRating: Double,
    @SerialName("drain")
    val drain: Double,
    @SerialName("hit_length")
    val hitLength: Int,
    @SerialName("id")
    val id: Int,
    @SerialName("is_scoreable")
    val isScoreable: Boolean,
    @SerialName("last_updated")
    val lastUpdated: CustomLocalDateTime,
    @SerialName("mode")
    val mode: String,
    @SerialName("mode_int")
    val modeInt: Int,
    @SerialName("passcount")
    val passcount: Int,
    @SerialName("playcount")
    val playcount: Int,
    @SerialName("ranked")
    val ranked: Int,
    @SerialName("status")
    val status: String,
    @SerialName("total_length")
    val totalLength: Int,
    @SerialName("url")
    val url: String,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("version")
    val version: String,
    @SerialName("beatmapset")
    val beatmapset: BeatmapSet? = null,
)

@Serializable
public data class BeatmapSet(
    @SerialName("artist")
    val artist: String,
    @SerialName("artist_unicode")
    val artistUnicode: String,
    @SerialName("covers")
    val covers: Map<String, String>,
    @SerialName("creator")
    val creator: String,
    @SerialName("favourite_count")
    val favouriteCount: Int,
    @SerialName("id")
    val id: Int,
    @SerialName("nsfw")
    val nsfw: Boolean,
    @SerialName("play_count")
    val playCount: Int,
    @SerialName("preview_url")
    val previewUrl: String,
    @SerialName("source")
    val source: String,
    @SerialName("status")
    val status: String,
    @SerialName("title")
    val title: String,
    @SerialName("title_unicode")
    val titleUnicode: String,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("video")
    val video: Boolean
)

@Serializable
public data class ScoreStatisticsDTO(
    @SerialName("count_100")
    val count100: Int,
    @SerialName("count_300")
    val count300: Int,
    @SerialName("count_50")
    val count50: Int,
    @SerialName("count_geki")
    val countGeki: Int,
    @SerialName("count_katu")
    val countKatu: Int,
    @SerialName("count_miss")
    val countMiss: Int
)