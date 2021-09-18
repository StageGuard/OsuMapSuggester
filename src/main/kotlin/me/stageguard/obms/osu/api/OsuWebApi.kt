package me.stageguard.obms.osu.api

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.bot.networkProcessorDispatcher
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.frontend.route.AUTH_CALLBACK_PATH
import me.stageguard.obms.osu.api.dto.*
import me.stageguard.obms.utils.InferredEitherOrISE
import me.stageguard.obms.utils.ValueOrISE
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.ifRight
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.mapLeft
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import net.mamoe.mirai.utils.info
import java.io.InputStream

@OptIn(ExperimentalSerializationApi::class)
object OsuWebApi {
    const val BASE_URL_V2 = "https://osu.ppy.sh/api/v2"
    const val BASE_URL_V1 = "https://osu.ppy.sh/api"
    const val BASE_URL_OLD = "https://old.ppy.sh"

    val json = Json { ignoreUnknownKeys = true }
    val client = HttpClient(OkHttp) { expectSuccess = false }

    private const val MAX_IN_ONE_REQ = 50
    /**
     * Auth related
     */
    suspend fun getTokenWithCode(
        code: String
    ): ValueOrISE<GetAccessTokenResponseDTO> = postImpl(
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
    ): ValueOrISE<GetAccessTokenResponseDTO> = postImpl(
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
    ) = getImpl<String, ValueOrISE<GetUserDTO>>(
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

    suspend fun getBeatmapFileStream(bid: Int) = openStream(
        url = "$BASE_URL_OLD/osu/$bid",
        parameters = mapOf(),
        headers = mapOf()
    )

    suspend fun getReplay(scoreId: Long) = getImpl<String, ValueOrISE<GetReplayDTO>>(
        url = "$BASE_URL_V1/get_replay",
        parameters = mapOf(
            "k" to PluginConfig.osuAuth.v1ApiKey,
            "s" to scoreId
        ),
        headers = mapOf()
    ) {
        if(contains("error")) {
            Either(IllegalStateException("REPLAY_NOT_AVAILABLE"))
        } else {
            InferredEitherOrISE(json.decodeFromString(this))
        }
    }

    suspend fun users(user: Long): ValueOrISE<GetUserDTO> =
        get("/users/${kotlin.run {
            OsuUserInfo.getOsuId(user) ?: return Either(IllegalStateException("NOT_BIND"))
        }}", user)

    suspend fun userScore(
        user: Long, mode: String = "osu",
        type: String = "recent", includeFails: Boolean = false,
        limit: Int = 10, offset: Int = 0
    //Kotlin bug: Result<T> is cast to java.util.List, use Either instead.
    ): ValueOrISE<List<ScoreDTO>> {
        val userId = OsuUserInfo.getOsuId(user) ?: return Either(IllegalStateException("NOT_BIND"))
        val initialList: MutableList<ScoreDTO> = mutableListOf()
        suspend fun getTailrec(current: Int = offset) : ValueOrISE<Unit> {
            return try {
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
                        "mode" to mode, "include_fails" to if(includeFails) 1 else 0,
                        "limit" to limit + offset - current, "offset" to current
                    )).also { re ->
                        re.onRight { li ->
                            initialList.addAll(li)
                        }.onLeft {
                            return Either(it)
                        }
                    }
                }
                InferredEitherOrISE(Unit)
            } catch (ex: IllegalStateException) {
                Either(ex)
            }
        }

        @Suppress("UNCHECKED_CAST")
        return getTailrec().run {
            ifRight {
                if(initialList.isNotEmpty()) {
                    InferredEitherOrISE(initialList)
                } else {
                    Either(IllegalStateException("SCORE_LIST_EMPTY"))
                }
            } ?: Either(left)
        }
    }

    suspend fun getBeatmap(
        user: Long, beatmapId: Int
    ) : ValueOrISE<BeatmapDTO> =
        get(path = "/beatmaps/$beatmapId/", user = user)

    suspend fun userBeatmapScore(
        user: Long, beatmapId: Int, mode: String = "osu"
    ) : ValueOrISE<BeatmapUserScoreDTO> {
        val userId = OsuUserInfo.getOsuId(user) ?: return Either(IllegalStateException("NOT_BIND"))

        return get<BeatmapUserScoreDTO>(
            path = "/beatmaps/$beatmapId/scores/users/$userId",
            parameters = mapOf("mode" to mode), user = user
        ).mapLeft {
            if(it.toString().contains("null")) {
                IllegalStateException("SCORE_LIST_EMPTY")
            } else IllegalStateException(it.localizedMessage)
        }
    }

    suspend fun me(user: Long): ValueOrISE<GetUserDTO> = get("/me", user = user)


    /**
     * implementations
     */
    suspend inline fun <reified REQ, reified RESP> post(
        path: String, user: Long, body: @Serializable REQ
    ): ValueOrISE<RESP> = postImpl(
        url = BASE_URL_V2 + path,
        token = OAuthManager.refreshTokenInNeedAndGet(user).getOrThrow(),
        body = body
    )

    suspend inline fun <reified RESP> get(
        path: String, user: Long, parameters: Map<String, Any> = mapOf()
    ) = getImpl<String, ValueOrISE<RESP>>(
        url = BASE_URL_V2 + path,
        headers = mapOf("Authorization" to "Bearer ${OAuthManager.refreshTokenInNeedAndGet(user).getOrThrow()}"),
        parameters = parameters
    ) {
        try {
            if(startsWith("[")) {
                InferredEitherOrISE(
                    json.decodeFromString<ArrayResponseWrapper<RESP>>("""
                        { "array": $this }
                    """.trimIndent()).data)
            } else {
                InferredEitherOrISE(json.decodeFromString(this))
            }
        } catch(ex: Exception) {
            Either(IllegalStateException("BAD_RESPONSE:$ex, response string: $this"))
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
        parameters: Map<String, Any>,
        headers: Map<String, String>,
        crossinline consumer: RESP.() -> R
    ) = withContext(networkProcessorDispatcher) {
        client.get<RESP> {
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
    }

    @Suppress("DuplicatedCode")
    suspend inline fun head(
        url: String,
        parameters: Map<String, String>,
        headers: Map<String, String>
    ) = withContext(networkProcessorDispatcher) {
        client.head<HttpStatement> {
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
    }

    @Suppress("DuplicatedCode")
    suspend inline fun <reified REQ, reified RESP : Any> postImpl(
        url: String, token: String? = null, body: @Serializable REQ
    ) = withContext<ValueOrISE<RESP>>(networkProcessorDispatcher) {
        val responseText = client.post<String> {
            url(url.also {
                OsuMapSuggester.logger.info { "POST: $url" }
            })
            if(token != null) header("Authorization", "Bearer $token")
            this.body = json.encodeToString(body)
            contentType(ContentType.Application.Json)
        }

        try {
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