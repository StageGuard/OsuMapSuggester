package me.stageguard.obms.osu.api

import jakarta.annotation.Resource
import kotlinx.serialization.*
import me.stageguard.obms.*
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.database.model.OsuUserInfoEx
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.osu.api.dto.*
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.mapLeft
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class OsuWebApi {
    val BASE_URL_V2 = "https://osu.ppy.sh/api/v2"
    val BASE_URL_V1 = "https://osu.ppy.sh/api"
    val BASE_URL_OLD = "https://old.ppy.sh"

    @Value("\${osuAuth.v1ApiKey}") private lateinit var v1ApiKey: String

    @Resource
    lateinit var oAuthManager: OAuthManager
    @Resource
    lateinit var osuHttpClient: OsuHttpClient
    @Resource
    private lateinit var osuUserInfoEx: OsuUserInfoEx

    private val MAX_IN_ONE_REQ = 50

    /**
     * Api function related
     */

    suspend fun searchBeatmapSet(
        user: Long, keyword: String, mode: String = "osu",
        category: String = "", isRecommended: Boolean = false
    ) = get<BeatmapSetSearchDTO>(
        user = user, path = "/beatmapsets/search", parameters = mutableMapOf(
            "q" to keyword,
            "m" to when(mode) { "osu" -> 0; "taiko" -> 1; "catch" -> 2; "mania" -> 3; else -> 0 }
        ).also {
            if(category.isNotEmpty()) it["s"] = category
            if(isRecommended) it["c"] = "recommended"
        }
    )

    suspend fun getBeatmapFileStream(bid: Int) = osuHttpClient.openStream(
        url = "$BASE_URL_OLD/osu/$bid",
        parameters = mapOf(),
        headers = mapOf()
    )

    suspend fun getReplay(scoreId: Long) = osuHttpClient.get<String, GetReplayDTO>(
        url = "$BASE_URL_V1/get_replay",
        parameters = mapOf(
            "k" to v1ApiKey,
            "s" to scoreId
        ),
        headers = mapOf()
    ) {
        if (contains("error")) {
            throw ReplayNotAvailable(scoreId)
        } else {
            osuHttpClient.json.decodeFromString(this)
        }
    }

    suspend fun users(user: Long): OptionalValue<GetUserDTO> =
        usersViaUID(user, kotlin.run { osuUserInfoEx.getOsuId(user) ?: return Either(NotBindException(user)) })

    suspend fun usersViaUID(user: Long, uid: Int, mode: String = "osu"): OptionalValue<GetUserDTO> =
        get("/users/$uid", user, parameters = mapOf("mode" to mode))

    suspend fun userScore(
        user: Long, mode: String = "osu",
        type: String = "recent", includeFails: Boolean = false,
        limit: Int = 10, offset: Int = 0
    //Kotlin bug: Result<T> is cast to java.util.List, use Either instead.
    ): OptionalValue<List<ScoreDTO>> {
        val userId = osuUserInfoEx.getOsuId(user) ?: return Either(NotBindException(user))
        val initialList: MutableList<ScoreDTO> = mutableListOf()
        suspend fun getTailrec(current: Int = offset) : OptionalValue<Unit> {
            if(current + MAX_IN_ONE_REQ < limit + offset) {
                get<List<ScoreDTO>>("/users/$userId/scores/$type", user, mapOf(
                    "mode" to mode, "include_fails" to if(includeFails) "1" else "0",
                    "limit" to MAX_IN_ONE_REQ.toString(), "offset" to current.toString()
                )).also { re ->
                    re.onRight { li ->
                        initialList.addAll(li)
                    }.onLeft {
                        return Either(it)
                    }
                }
                return getTailrec(current + MAX_IN_ONE_REQ)
            } else {
                get<List<ScoreDTO>>("/users/$userId/scores/$type", user, mapOf(
                    "mode" to mode, "include_fails" to if(includeFails) 1 else 0,
                    "limit" to limit + offset - current, "offset" to current
                )).also { re ->
                    re.onRight { li ->
                        initialList.addAll(li)
                    }.onLeft {
                        return Either(it)
                    }
                }
                return InferredOptionalValue(Unit)
            }
        }

        getTailrec().onRight {
            return if(initialList.isNotEmpty()) InferredOptionalValue(initialList) else Either(UserScoreEmptyException(user))
        }.left.also { return Either(it) }
    }

    suspend fun getBeatmap(
        user: Long, beatmapId: Int
    ) : OptionalValue<BeatmapDTO> =
        get(path = "/beatmaps/$beatmapId/", user = user)

    suspend fun userBeatmapScore(
        user: Long, beatmapId: Int,
        mode: String = "osu", mods: List<String> = listOf()
    ) : OptionalValue<BeatmapUserScoreDTO> {
        val userId = osuUserInfoEx.getOsuId(user) ?: return Either(NotBindException(user))
        val queryParameters = mutableMapOf<String, Any>("mode" to mode)
        if (mods.isNotEmpty()) queryParameters["mods"] = mods

        return get<BeatmapUserScoreDTO>(
            path = "/beatmaps/$beatmapId/scores/users/$userId",
            parameters = queryParameters, user = user
        ).mapLeft {
            if(it is BadResponseException && it.toString().contains("null")) {
                BeatmapScoreEmptyException(beatmapId)
            } else it
        }
    }

    suspend fun me(user: Long): OptionalValue<GetOwnDTO> = get("/me", user = user)



    /**
     * implementations
     */
    suspend inline fun <reified REQ, reified RESP> post(
        path: String, user: Long, body: @Serializable REQ
    ) = osuHttpClient.post<REQ, RESP>(
        url = BASE_URL_V2 + path,
        token = oAuthManager.getBindingToken(user).rightOrThrowLeft(),
        body = body
    )

    suspend inline fun <reified RESP> get(
        path: String, user: Long, parameters: Map<String, Any> = mapOf()
    ) = osuHttpClient.get<String, RESP>(
        url = BASE_URL_V2 + path,
        headers = mapOf("Authorization" to "Bearer ${oAuthManager.getBindingToken(user).rightOrThrowLeft()}"),
        parameters = parameters
    ) {
        try {
            if(startsWith("[")) {
                osuHttpClient.json.decodeFromString<ArrayResponseWrapper<RESP>>("""
                { "array": $this }
            """.trimIndent()).data
            } else {
                osuHttpClient.json.decodeFromString(this)
            }
        } catch (ex: SerializationException) {
            if(contains("authentication") && contains("basic")) {
                throw InvalidTokenException(user)
            } else {
                throw BadResponseException(BASE_URL_V2 + path, this).suppress(ex)
            }
        }
    }

}

@Serializable
data class ArrayResponseWrapper<T>(
    @SerialName("array")
    val data: @Serializable T
)
