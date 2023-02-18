package me.stageguard.osu.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
public data class OsuUser(
    @SerialName("avatar_url")
    val avatarUrl: String,
    @SerialName("country")
    val country: Country,
    @SerialName("country_code")
    val countryCode: String,
    @SerialName("id")
    val id: Int,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("is_deleted")
    val isDeleted: Boolean,
    @SerialName("is_online")
    val isOnline: Boolean,
    @SerialName("monthly_playcounts")
    val monthlyPlayCounts: List<MonthlyPlayCount>,
    @SerialName("statistics")
    val statistics: UserStatistics,
    @SerialName("username")
    val username: String
)

@Serializable
public data class GetOwnDTO(
    @SerialName("avatar_url")
    val avatarUrl: String,
    @SerialName("country")
    val country: Country,
    @SerialName("country_code")
    val countryCode: String,
    @SerialName("id")
    val id: Int,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("is_deleted")
    val isDeleted: Boolean,
    @SerialName("is_online")
    val isOnline: Boolean,
    @SerialName("monthly_playcounts")
    val monthlyPlayCounts: List<MonthlyPlayCount>,
    @SerialName("statistics")
    val statistics: UserStatistics,
    @SerialName("username")
    val username: String,
    @SerialName("cover_url")
    val coverUrl: String,
    @SerialName("discord")
    val discord: String?,
    @SerialName("twitter")
    val twitter: String?,
    @SerialName("website")
    val website: String?,
    @SerialName("rank_history")
    val rankHistory: RankHistory
)

@Serializable
public data class Country(
    @SerialName("code")
    val code: String,
    @SerialName("name")
    val name: String
)

@Serializable
public data class MonthlyPlayCount(
    @SerialName("count")
    val count: Int,
    @SerialName("start_date")
    val startDate: String
)


@Serializable
public data class RankHistory(
    @SerialName("data")
    val `data`: List<Int>,
    @SerialName("mode")
    val mode: String
)

@Serializable
public data class UserStatistics(
    @SerialName("grade_counts")
    val gradeCounts: GradeCounts,
    @SerialName("hit_accuracy")
    val hitAccuracy: Double,
    @SerialName("is_ranked")
    val isRanked: Boolean,
    @SerialName("level")
    val level: Level,
    @SerialName("maximum_combo")
    val maximumCombo: Long,
    @SerialName("play_count")
    val playCount: Int,
    @SerialName("play_time")
    val playTime: Long,
    @SerialName("pp")
    val pp: Double? = null,
    @SerialName("ranked_score")
    val rankedScore: Long,
    @SerialName("replays_watched_by_others")
    val replaysWatchedByOthers: Long,
    @SerialName("total_hits")
    val totalHits: Long,
    @SerialName("total_score")
    val totalScore: Long,
    @SerialName("global_rank")
    val globalRank: Long? = null,
    @SerialName("country_rank")
    val countryRank: Long? = null,

    )

@Serializable
public data class GradeCounts(
    @SerialName("a")
    val a: Int,
    @SerialName("s")
    val s: Int,
    @SerialName("sh")
    val sh: Int,
    @SerialName("ss")
    val ss: Int,
    @SerialName("ssh")
    val ssh: Int
)

@Serializable
public data class Level(
    @SerialName("current")
    val current: Int,
    @SerialName("progress")
    val progress: Int
)