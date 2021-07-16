package me.stageguard.obms.api.osu

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.api.osu.dto.GetAccessTokenRequestDTO
import me.stageguard.obms.api.osu.dto.GetAccessTokenResponseDTO
import me.stageguard.obms.api.osu.dto.GetUserDTO
import me.stageguard.obms.api.osu.dto.RefreshTokenRequestDTO
import me.stageguard.obms.api.osu.oauth.OAuthManager
import me.stageguard.obms.database.model.User
import me.stageguard.obms.database.model.findByQQ
import me.stageguard.obms.frontend.route.AUTH_CALLBACK_PATH
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.utils.info
import java.lang.IllegalStateException

object OsuWebApi {
    const val BASE_URL = "https://osu.ppy.sh/api/v2"
    val client = HttpClient(OkHttp)
    val json = Json { ignoreUnknownKeys = true }

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
            redirectUri = "http://${PluginConfig.frontend.authCallbackShow}:${PluginConfig.frontend.port}/$AUTH_CALLBACK_PATH"
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
            redirectUri = "http://${PluginConfig.frontend.authCallbackShow}:${PluginConfig.frontend.port}/$AUTH_CALLBACK_PATH"
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

    suspend fun users(user: Long): Result<GetUserDTO> = get("/users/${kotlin.run {
        User.findByQQ(user) ?: return Result.failure(IllegalStateException("NOT_BIND"))
    }}", user)

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

    @Suppress("DuplicatedCode")
    suspend inline fun <reified REQ, reified RESP> postImpl(
        url: String, token: String? = null, body: @Serializable REQ
    ) : Result<RESP> = client.post<HttpStatement> {
        url(url)
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

    @Suppress("DuplicatedCode")
    suspend inline fun <reified RESP> getImpl(
        url: String, token: String, parameters: Map<String, String>
    ) : Result<RESP> = client.get<HttpStatement> {
        url(url)
        header("Authorization", "Bearer $token")
        parameters.forEach {
            parameter(it.key, it.value)
        }
    }.execute {
        if(it.status.isSuccess()) {
            val content = it.content
            Result.success(json.decodeFromString<RESP>(buildString {
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
}