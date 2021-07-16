package me.stageguard.obms.api.osu.dto
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName


@Serializable
data class GetUserDTO(
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
    @SerialName("is_restricted")
        val isRestricted: Boolean,
    @SerialName("monthly_playcounts")
        val monthlyPlaycounts: List<MonthlyPlaycount>,
    @SerialName("pm_friends_only")
        val pmFriendsOnly: Boolean,
    @SerialName("rank_history")
        val rankHistory: RankHistory,
    @SerialName("scores_first_count")
        val scoresFirstCount: Int,
    @SerialName("statistics")
        val statistics: Statistics,
    @SerialName("username")
        val username: String
)

@Serializable
data class Country(
    @SerialName("code")
    val code: String,
    @SerialName("name")
    val name: String
)

@Serializable
data class MonthlyPlaycount(
    @SerialName("count")
    val count: Int,
    @SerialName("start_date")
    val startDate: String
)


@Serializable
data class RankHistory(
    @SerialName("data")
    val `data`: List<Int>,
    @SerialName("mode")
    val mode: String
)

@Serializable
data class Statistics(
    @SerialName("global_rank")
    val globalRank: Long,
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
    val pp: Double,
    @SerialName("rank")
    val rank: Rank,
    @SerialName("ranked_score")
    val rankedScore: Long,
    @SerialName("replays_watched_by_others")
    val replaysWatchedByOthers: Long,
    @SerialName("total_hits")
    val totalHits: Long,
    @SerialName("total_score")
    val totalScore: Long
)

@Serializable
data class GradeCounts(
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
data class Level(
    @SerialName("current")
    val current: Int,
    @SerialName("progress")
    val progress: Int
)

@Serializable
data class Rank(
    @SerialName("country")
    val country: Long,
    @SerialName("global")
    val global: Long? = null
)