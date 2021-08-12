package me.stageguard.obms.osu.api

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.database.model.User
import me.stageguard.obms.database.model.getOsuIdSuspend
import me.stageguard.obms.frontend.route.AUTH_CALLBACK_PATH
import me.stageguard.obms.osu.api.dto.*
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.success
import net.mamoe.mirai.utils.info
import java.io.InputStream
import java.lang.IllegalStateException

object OsuWebApi {
    const val BASE_URL = "https://osu.ppy.sh/api/v2"
    val client = HttpClient(OkHttp)
    val json = Json { ignoreUnknownKeys = true }

    private const val MAX_IN_ONE_REQ = 50
    /**
     * Auth related
     */
    @Suppress("HttpUrlsUsage")
    suspend fun getTokenWithCode(code: String): Result<GetAccessTokenResponseDTO> = postImpl(
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

    @Suppress("HttpUrlsUsage")
    suspend fun refreshToken(refToken: String): Result<GetAccessTokenResponseDTO> = postImpl(
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

    suspend fun getSelfProfileAfterVerifyToken(token: String): Result<GetUserDTO> = getImpl(
        url = "$BASE_URL/me",
        token = token,
        parameters = mapOf()
    )

    /**
     * Api function related
     */

    suspend fun getBeatmap(bid: Int) = openStream(
        url = "https://old.ppy.sh/osu/$bid",
        parameters = mapOf()
    ) { this }

    suspend fun users(user: Long): Result<GetUserDTO> = get("/users/${kotlin.run {
        User.getOsuIdSuspend(user) ?: return Result.failure(IllegalStateException("NOT_BIND"))
    }}", user)

    suspend fun userScore(
        user: Long, mode: String = "osu",
        type: String = "recent", includeFails: Boolean = false,
        limit: Int = 10, offset: Int = 0
    //Kotlin bug: Result<T> is cast to java.util.List, use Either instead.
    ): Either<List<ScoreDTO>, IllegalStateException> {
        val userId = User.getOsuIdSuspend(user) ?: return Either.Right(IllegalStateException("NOT_BIND"))
        val initialList: MutableList<ScoreDTO> = mutableListOf()
        suspend fun getTailrec(current: Int = offset) : Result<Unit> {
            if(current + MAX_IN_ONE_REQ < limit + offset) {
                get<List<ScoreDTO>>("/users/$userId/scores/$type", user, mapOf(
                    "mode" to mode, "include_fails" to if(includeFails) "1" else "0",
                    "limit" to MAX_IN_ONE_REQ.toString(), "offset" to current.toString()
                )).also { re ->
                    re.onSuccess { li ->
                        initialList.addAll(li)
                    }.onFailure {
                        return Result.failure(it)
                    }
                }
                getTailrec(current + MAX_IN_ONE_REQ)
            } else {
                get<List<ScoreDTO>>("/users/$userId/scores/$type", user, mapOf(
                    "mode" to mode, "include_fails" to if(includeFails) "1" else "0",
                    "limit" to (limit + offset - current).toString(), "offset" to current.toString()
                )).also { re ->
                    re.onSuccess { li ->
                        initialList.addAll(li)
                    }.onFailure {
                        return Result.failure(it)
                    }
                }
            }
            return Result.success()
        }
        val result = getTailrec()
        return if(result.isSuccess) Either.Left(initialList.toList()) else Either.Right(result.exceptionOrNull()!! as IllegalStateException)
    }

    suspend fun me(user: Long): Result<GetUserDTO> = get("/me", user = user)


    /**
     * implementations
     */
    private suspend inline fun <reified REQ, reified RESP> post(
        path: String, user: Long, body: @Serializable REQ
    ): Result<RESP> = postImpl(
        url = BASE_URL + path,
        token = OAuthManager.refreshTokenInNeedAndGet(user).getOrThrow(),
        body = body
    )

    suspend inline fun <reified RESP> get(
        path: String, user: Long, parameters: Map<String, String> = mapOf()
    ): Result<RESP> = getImpl(
        url = BASE_URL + path,
        token = OAuthManager.refreshTokenInNeedAndGet(user).getOrThrow(),
        parameters = parameters
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("DuplicatedCode")
    suspend inline fun <reified REQ, reified RESP> postImpl(
        url: String, token: String? = null, body: @Serializable REQ
    ) : Result<RESP> = client.post<HttpStatement> {
        url(url.also {
            OsuMapSuggester.logger.info { "POST: $url" }
        })
        if(token != null) header("Authorization", "Bearer $token")
        this.body = json.encodeToString(body)
        contentType(ContentType.Application.Json)
    }.execute {
        if(it.status.isSuccess()) {
            val content = it.content
            Result.success(json.decodeFromString(buildString {
                var line = content.readUTF8Line()
                while(line != null) {
                    append(line)
                    line = content.readUTF8Line()
                }
            }))
        } else {
            Result.failure(IllegalStateException("BAD_RESPONSE:${it.status.value}"))
        }
    }

    @OptIn(ExperimentalStdlibApi::class, ExperimentalSerializationApi::class)
    @Suppress("DuplicatedCode")
    suspend inline fun <reified RESP> getImpl(
        url: String, token: String, parameters: Map<String, String>
    ) : Result<RESP> = client.get<HttpStatement> {
        url(url.also {
            OsuMapSuggester.logger.info {
                "GET: $url${parameters.map { "${it.key}=${it.value}" }.joinToString("&").run {
                    if(isNotEmpty()) "?$this" else ""
                }}"
            }
        })
        header("Authorization", "Bearer $token")
        parameters.forEach {
            parameter(it.key, it.value)
        }
    }.execute {
        if(it.status.isSuccess()) {
            val content = it.content.run { buildString {
                var line = readUTF8Line()
                while(line != null) {
                    append(line)
                    line = readUTF8Line()
                }
            } }
            if(content.startsWith("[")) Result.success(
                json.decodeFromString<ArrayResponseWrapper<RESP>>("""
                    { "array": $content }
                """.trimIndent()).data) else Result.success(json.decodeFromString(content))
        } else {
            Result.failure(IllegalStateException("BAD_RESPONSE:${it.status.value}"))
        }
    }

    @Suppress("DuplicatedCode")
    suspend inline fun <R> openStream(
        url: String, parameters: Map<String, String>, consumer: InputStream.() -> R
    ) = client.get<InputStream> {
        url(url.also {
            OsuMapSuggester.logger.info {
                "GET: $url${parameters.map { "${it.key}=${it.value}" }.joinToString("&").run { 
                    if(isNotEmpty()) "?$this" else ""
                }}"
            }
        })
        parameters.forEach {
            parameter(it.key, it.value)
        }
    }.run(consumer)

    fun closeClient() = client.close()
}

@Serializable
data class ArrayResponseWrapper<T>(
    @SerialName("array")
    val data: @Serializable T
)