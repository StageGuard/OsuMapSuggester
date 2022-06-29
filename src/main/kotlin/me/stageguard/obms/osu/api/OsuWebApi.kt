package me.stageguard.obms.osu.api

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import me.stageguard.obms.*
import me.stageguard.obms.bot.networkProcessorDispatcher
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.frontend.route.AUTH_CALLBACK_PATH
import me.stageguard.obms.osu.api.dto.*
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.left
import me.stageguard.obms.utils.Either.Companion.mapLeft
import me.stageguard.obms.utils.Either.Companion.onLeft
import me.stageguard.obms.utils.Either.Companion.onRight
import net.mamoe.mirai.utils.info
import java.io.InputStream
import kotlin.properties.Delegates

@OptIn(ExperimentalSerializationApi::class)
object OsuWebApi {
    const val BASE_URL_V2 = "https://osu.ppy.sh/api/v2"
    const val BASE_URL_V1 = "https://osu.ppy.sh/api"
    const val BASE_URL_OLD = "https://old.ppy.sh"

    val json = Json { ignoreUnknownKeys = true }
    @OptIn(KtorExperimentalAPI::class)
    val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout)

        if (PluginConfig.clientProxy.isNotBlank()) {
            engine { proxy = ProxyBuilder.http(PluginConfig.clientProxy) }
        }
    }

    private const val MAX_IN_ONE_REQ = 50

    /**
     * Auth related
     */

    suspend fun getTokenWithCode(
        code: String
    ): OptionalValue<GetAccessTokenResponseDTO> = postImpl(
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
    ): OptionalValue<GetAccessTokenResponseDTO> = postImpl(
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
    ) = getImpl<String, GetUserDTO>(
        url = "$BASE_URL_V2/me",
        parameters = mapOf(),
        headers = mapOf("Authorization" to "Bearer $token")
    ) { json.decodeFromString(this) }

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

    suspend fun getReplay(scoreId: Long) = getImpl<String, GetReplayDTO>(
        url = "$BASE_URL_V1/get_replay",
        parameters = mapOf(
            "k" to PluginConfig.osuAuth.v1ApiKey,
            "s" to scoreId
        ),
        headers = mapOf()
    ) {
        if (contains("error")) {
            throw ReplayNotAvailable(scoreId)
        } else {
            json.decodeFromString(this)
        }
    }

    suspend fun users(user: Long): OptionalValue<GetUserDTO> =
        usersViaUID(user, kotlin.run { OsuUserInfo.getOsuId(user) ?: return Either(NotBindException(user)) })

    suspend fun usersViaUID(user: Long, uid: Int, mode: String = "osu"): OptionalValue<GetUserDTO> =
        get("/users/$uid", user, parameters = mapOf("mode" to mode))

    suspend fun userScore(
        user: Long, mode: String = "osu",
        type: String = "recent", includeFails: Boolean = false,
        limit: Int = 10, offset: Int = 0
    //Kotlin bug: Result<T> is cast to java.util.List, use Either instead.
    ): OptionalValue<List<ScoreDTO>> {
        val userId = OsuUserInfo.getOsuId(user) ?: return Either(NotBindException(user))
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
        val userId = OsuUserInfo.getOsuId(user) ?: return Either(NotBindException(user))
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

    suspend fun me(user: Long): OptionalValue<GetUserDTO> = get("/me", user = user)


    /**
     * implementations
     */
    suspend inline fun <reified REQ, reified RESP> post(
        path: String, user: Long, body: @Serializable REQ
    ) = postImpl<REQ, RESP>(
        url = BASE_URL_V2 + path,
        token = OAuthManager.getBindingToken(user).rightOrThrowLeft(),
        body = body
    )

    suspend inline fun <reified RESP> get(
        path: String, user: Long, parameters: Map<String, Any> = mapOf()
    ) = getImpl<String, RESP>(
        url = BASE_URL_V2 + path,
        headers = mapOf("Authorization" to "Bearer ${OAuthManager.getBindingToken(user).rightOrThrowLeft()}"),
        parameters = parameters
    ) {
        try {
            if(startsWith("[")) {
                json.decodeFromString<ArrayResponseWrapper<RESP>>("""
                { "array": $this }
            """.trimIndent()).data
            } else {
                json.decodeFromString(this)
            }
        } catch (ex: SerializationException) {
            if(contains("authentication") && contains("basic")) {
                throw InvalidTokenException(user)
            } else {
                throw BadResponseException(BASE_URL_V2 + path, this).suppress(ex)
            }
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun openStream(
        url: String,
        parameters: Map<String, String>,
        headers: Map<String, String>,
    ) = getImpl<InputStream, InputStream>(url, parameters, headers) { this }

    @Suppress("DuplicatedCode")
    suspend inline fun <reified RESP, reified R> getImpl(
        url: String,
        parameters: Map<String, Any>,
        headers: Map<String, String>,
        crossinline consumer: RESP.() -> R
    ): OptionalValue<R> = withContext(networkProcessorDispatcher) {
        try {
            client.get<RESP> {
                url(buildString {
                    append(url)
                    if (parameters.isNotEmpty()) {
                        append("?")
                        parameters.forEach { (k, v) ->
                            if (v is List<*> || v is MutableList<*>) {
                                val arrayParameter = (v as List<*>)
                                arrayParameter.forEachIndexed { idx, lv ->
                                    append("$k[]=$lv")
                                    if(idx != arrayParameter.lastIndex) append("&")
                                }
                            } else {
                                append("$k=$v")
                            }
                            append("&")
                        }
                    }
                }.run {
                    if (last() == '&') dropLast(1) else this
                }.also { OsuMapSuggester.logger.info { "GET: $it" } })
                headers.forEach {
                    header(it.key, it.value)
                }
            }.run { InferredOptionalValue(consumer(this)) }
        } catch (ex: Exception) {
            when(ex) {
                is RefactoredException -> Either(ex)
                is SocketTimeoutException,
                is HttpRequestTimeoutException,
                is ConnectTimeoutException -> {
                    Either(ApiRequestTimeoutException(url).suppress(ex))
                }
                else -> {
                    Either(UnhandledException(ex))
                }
            }
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun head(
        url: String,
        parameters: Map<String, Any>,
        headers: Map<String, String>
    ): OptionalValue<Headers> = withContext(networkProcessorDispatcher) {
        try {
            client.head<HttpStatement> {
                url(buildString {
                    append(url)
                    if (parameters.isNotEmpty()) {
                        append("?")
                        parameters.forEach { (k, v) ->
                            if (v is List<*> || v is MutableList<*>) {
                                val arrayParameter = (v as List<*>)
                                arrayParameter.forEachIndexed { idx, lv ->
                                    append("$k[]=$lv")
                                    if(idx != arrayParameter.lastIndex) append("&")
                                }
                            } else {
                                append("$k=$v")
                            }
                            append("&")
                        }
                    }
                }.run {
                    if (last() == '&') dropLast(1) else this
                }.also { OsuMapSuggester.logger.info { "HEAD: $it" } })
                headers.forEach { header(it.key, it.value) }
            }.execute { InferredOptionalValue(it.headers) }
        } catch (ex: Exception) {
            when(ex) {
                is SocketTimeoutException,
                is HttpRequestTimeoutException,
                is ConnectTimeoutException -> {
                    Either(ApiRequestTimeoutException(url).suppress(ex))
                }
                else -> {
                    Either(UnhandledException(ex))
                }
            }
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun <reified REQ, reified RESP> postImpl(
        url: String, token: String? = null, body: @Serializable REQ
    ): OptionalValue<RESP> = withContext(networkProcessorDispatcher) {
        var responseText by Delegates.notNull<String>()
        try {
            responseText = client.post {
                url(url.also {
                    OsuMapSuggester.logger.info { "POST: $url" }
                })
                if(token != null) header("Authorization", "Bearer $token")
                this.body = json.encodeToString(body)
                contentType(ContentType.Application.Json)
            }

            InferredOptionalValue(json.decodeFromString(responseText))
        } catch (ex: Exception) {
            when(ex) {
                is SocketTimeoutException,
                is HttpRequestTimeoutException,
                is ConnectTimeoutException -> {
                    Either(ApiRequestTimeoutException(url).suppress(ex))
                }
                is SerializationException -> {
                    Either(BadResponseException(url, responseText).suppress(ex))
                }
                else -> {
                    Either(UnhandledException(ex))
                }
            }
        }
    }

    fun closeClient() = kotlin.runCatching {
        client.close()
    }
}

@Serializable
data class ArrayResponseWrapper<T>(
    @SerialName("array")
    val data: @Serializable T
)
