package me.stageguard.obms.osu.api

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.database.model.User
import me.stageguard.obms.database.model.getOsuIdSuspend
import me.stageguard.obms.frontend.route.AUTH_CALLBACK_PATH
import me.stageguard.obms.osu.api.dto.*
import me.stageguard.obms.utils.InferredEitherOrISE
import me.stageguard.obms.utils.ValueOrIllegalStateException
import net.mamoe.mirai.utils.Either
import net.mamoe.mirai.utils.Either.Companion.ifRight
import net.mamoe.mirai.utils.Either.Companion.left
import net.mamoe.mirai.utils.Either.Companion.onLeft
import net.mamoe.mirai.utils.Either.Companion.onRight
import net.mamoe.mirai.utils.Either.Companion.rightOrNull
import net.mamoe.mirai.utils.info
import java.io.InputStream

object OsuWebApi {
    const val BASE_URL_V2 = "https://osu.ppy.sh/api/v2"
    const val BASE_URL_V1 = "https://osu.ppy.sh/api"
    const val BASE_URL_OLD = "https://old.ppy.sh"

    val client = HttpClient(OkHttp) {
        expectSuccess = false
    }
    val json = Json { ignoreUnknownKeys = true }

    private const val MAX_IN_ONE_REQ = 50
    /**
     * Auth related
     */
    suspend fun getTokenWithCode(
        code: String
    ): ValueOrIllegalStateException<GetAccessTokenResponseDTO> = postImpl(
        url = "https://osu.ppy.sh/oauth/token",
        token = null,
        body = GetAccessTokenRequestDTO(
            clientId = PluginConfig.osuAuth.clientId,
            clientSecret = PluginConfig.osuAuth.secret,
            grantType = "authorization_code",
            code = code,
            redirectUri = "${PluginConfig.osuAuth.authCallbackBaseUrl}/$AUTH_CALLBACK_PATH"
        )
    )

    suspend fun refreshToken(
        refToken: String
    ): ValueOrIllegalStateException<GetAccessTokenResponseDTO> = postImpl(
        url = "https://osu.ppy.sh/oauth/token",
        token = null,
        body = RefreshTokenRequestDTO(
            clientId = PluginConfig.osuAuth.clientId,
            clientSecret = PluginConfig.osuAuth.secret,
            grantType = "refresh_token",
            refreshToken = refToken,
            redirectUri = "${PluginConfig.osuAuth.authCallbackBaseUrl}/$AUTH_CALLBACK_PATH"
        )
    )

    suspend fun getSelfProfileAfterVerifyToken(
        token: String
    ) = getImpl<String, ValueOrIllegalStateException<GetUserDTO>>(
        url = "$BASE_URL_V2/me",
        parameters = mapOf(),
        headers = mapOf("Authorization" to "Bearer $token")
    ) {
        try {
            InferredEitherOrISE(json.decodeFromString(this))
        } catch (ex: IllegalStateException) {
            Either(ex)
        }
    }

    /**
     * Api function related
     */

    suspend fun getBeatmap(bid: Int) = openStream(
        url = "$BASE_URL_OLD/osu/$bid",
        parameters = mapOf(),
        headers = mapOf()
    )

    suspend fun getReplay(scoreId: Int) = getImpl<String, ValueOrIllegalStateException<GetReplayDTO>>(
        url = "$BASE_URL_V1/get_replay",
        parameters = mapOf(
            "k" to PluginConfig.osuAuth.v1ApiKey,
            "s" to scoreId.toString()
        ),
        headers = mapOf()
    ) {
        if(contains("error")) {
            Either(IllegalStateException("REPLAY_NOT_AVAILABLE"))
        } else {
            Either(json.decodeFromString(this))
        }
    }

    suspend fun users(user: Long): ValueOrIllegalStateException<GetUserDTO> =
        get("/users/${kotlin.run {
            User.getOsuIdSuspend(user) ?: return Either(IllegalStateException("NOT_BIND"))
        }}", user)

    suspend fun userScore(
        user: Long, mode: String = "osu",
        type: String = "recent", includeFails: Boolean = false,
        limit: Int = 10, offset: Int = 0
    //Kotlin bug: Result<T> is cast to java.util.List, use Either instead.
    ): ValueOrIllegalStateException<List<ScoreDTO>> {
        val userId = User.getOsuIdSuspend(user) ?: return Either(IllegalStateException("NOT_BIND"))
        val initialList: MutableList<ScoreDTO> = mutableListOf()
        suspend fun getTailrec(current: Int = offset) : ValueOrIllegalStateException<Unit> {
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
                getTailrec(current + MAX_IN_ONE_REQ)
            } else {
                get<List<ScoreDTO>>("/users/$userId/scores/$type", user, mapOf(
                    "mode" to mode, "include_fails" to if(includeFails) "1" else "0",
                    "limit" to (limit + offset - current).toString(), "offset" to current.toString()
                )).also { re ->
                    re.onRight { li ->
                        initialList.addAll(li)
                    }.onLeft {
                        return Either(it)
                    }
                }
            }
            return InferredEitherOrISE(Unit)
        }

        //TODO: Kotlin inline bug: nested class
        //TODO: class cast shouldn't appear here
        @Suppress("UNCHECKED_CAST")
        return (getTailrec().value as ValueOrIllegalStateException<Unit>).run {
            ifRight {
                if(initialList.isNotEmpty()) {
                    InferredEitherOrISE(initialList)
                } else {
                    Either(IllegalStateException("SCORE_LIST_EMPTY"))
                }
            } ?: Either(left)
        }
    }

    suspend fun userBeatmapScore(
        user: Long, beatmapId: Int, mode: String = "osu"
    ) : ValueOrIllegalStateException<BeatmapUserScoreDTO> {
        val userId = User.getOsuIdSuspend(user) ?: return Either(IllegalStateException("NOT_BIND"))
        return try {
            get(
                path = "/beatmaps/$beatmapId/scores/users/$userId",
                parameters = mapOf("mode" to mode), user = user
            )
        } catch (ex: IllegalStateException) {
            Either(ex)
        }
    }

    suspend fun me(user: Long): ValueOrIllegalStateException<GetUserDTO> = get("/me", user = user)


    /**
     * implementations
     */
    suspend inline fun <reified REQ, reified RESP> post(
        path: String, user: Long, body: @Serializable REQ
    ): ValueOrIllegalStateException<RESP> = postImpl(
        url = BASE_URL_V2 + path,
        token = OAuthManager.refreshTokenInNeedAndGet(user).getOrThrow(),
        body = body
    )

    suspend inline fun <reified RESP> get(
        path: String, user: Long, parameters: Map<String, String> = mapOf()
    ) = getImpl<String, ValueOrIllegalStateException<RESP>>(
        url = BASE_URL_V2 + path,
        headers = mapOf("Authorization" to "Bearer ${OAuthManager.refreshTokenInNeedAndGet(user).getOrThrow()}"),
        parameters = parameters
    ) {
        try {
            if(startsWith("[")) Either(
                json.decodeFromString<ArrayResponseWrapper<RESP>>("""
                    { "array": $this }
                """.trimIndent()).data) else Either(json.decodeFromString(this))
        } catch(ex: Exception) {
            Either(IllegalStateException("BAD_RESPONSE:$this"))
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun openStream(
        url: String,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ) = getImpl<InputStream, InputStream>(url, parameters, headers) { this }

    @Suppress("DuplicatedCode")
    suspend inline fun <reified RESP, R> getImpl(
        url: String,
        parameters: Map<String, String>,
        headers: Map<String, String>,
        consumer: RESP.() -> R
    ) = client.get<RESP> {
        url(url.also {
            OsuMapSuggester.logger.info {
                "GET: $url${parameters.map { "${it.key}=${it.value}" }.joinToString("&").run {
                    if(isNotEmpty()) "?$this" else ""
                }}"
            }
        })
        headers.forEach {
            header(it.key, it.value)
        }
        parameters.forEach {
            parameter(it.key, it.value)
        }
    }.run(consumer)

    @Suppress("DuplicatedCode")
    suspend inline fun head(
        url: String,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ) = client.head<HttpStatement> {
        url(url.also {
            OsuMapSuggester.logger.info {
                "HEAD: $url${parameters.map { "${it.key}=${it.value}" }.joinToString("&").run {
                    if(isNotEmpty()) "?$this" else ""
                }}"
            }
        })
        headers.forEach { header(it.key, it.value) }
        parameters.forEach { parameter(it.key, it.value) }
    }.execute { it.headers }

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("DuplicatedCode")
    suspend inline fun <reified REQ, reified RESP : Any> postImpl(
        url: String, token: String? = null, body: @Serializable REQ
    ) : ValueOrIllegalStateException<RESP> {
        val responseText = client.post<String> {
            url(url.also {
                OsuMapSuggester.logger.info { "POST: $url" }
            })
            if(token != null) header("Authorization", "Bearer $token")
            this.body = json.encodeToString(body)
            contentType(ContentType.Application.Json)
        }

        return try {
            InferredEitherOrISE(json.decodeFromString(responseText))
        } catch (ex: Exception) {
            Either(IllegalStateException("BAD_RESPONSE:$responseText"))
        }
    }

    fun closeClient() = client.close()
}

@Serializable
data class ArrayResponseWrapper<T>(
    @SerialName("array")
    val data: @Serializable T
)