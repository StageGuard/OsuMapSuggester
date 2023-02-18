package me.stageguard.osu.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import me.stageguard.osu.api.dto.*
import xyz.cssxsh.rosu.GameMode

public class OsuWebApiClient(
    @PublishedApi internal val config: OsuWebApiClientConfig,
    @PublishedApi internal val holder: OsuWebApiTokenHolder
) : Closeable {
    public companion object {
        @PublishedApi
        internal val JSON: Json = Json {
            ignoreUnknownKeys = System.getProperty("me.stageguard.osu.api.json.ignore")?.toBooleanStrictOrNull() ?: true
            serializersModule = SerializersModule {
                contextual(OffsetDateTimeSerializer)
            }
        }
        @PublishedApi
        internal val SCOPE: String = System.getProperty("me.stageguard.osu.api.scope", "identify friends.read public")
    }

    @PublishedApi
    internal val http: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            socketTimeoutMillis = config.timeout
            connectTimeoutMillis = config.timeout
            requestTimeoutMillis = null
        }
        install(ContentNegotiation) {
            json(json = JSON)
        }
        Auth {
            bearer {
                sendWithoutRequest { request ->
                    request.url.host == "osu.ppy.sh" && request.url.pathSegments[0] != "oauth"
                }
                loadTokens {
                    holder.loadTokens()
                }
                refreshTokens {
                    val old = oldTokens
                    val response = client.post("https://osu.ppy.sh/oauth/token") {
                        contentType(ContentType.Application.Json)
                        if (old == null) {
                            setBody(body = GetAccessTokenRequest(
                                clientId = config.clientId,
                                clientSecret = config.secret,
                                grantType = "authorization_code",
                                code = holder.getCode(state = state),
                                redirectUri = redirectUri
                            ))
                        } else {
                            setBody(body = RefreshTokenRequest(
                                clientId = config.clientId,
                                clientSecret = config.secret,
                                grantType = "refresh_token",
                                refreshToken = old.refreshToken,
                                redirectUri = redirectUri
                            ))
                        }
                    }
                    val data = response.body<GetAccessTokenResponse>()

                    val tokens = BearerTokens(
                        accessToken = data.accessToken,
                        refreshToken = data.refreshToken
                    )

                    holder.saveTokens(tokens = tokens)

                    tokens
                }
            }
        }
        CurlUserAgent()
        ContentEncoding()
    }

    override fun close(): Unit = http.close()

    private val redirectUri get() = "${config.baseUrl}/authCallback"

    private var state: String = ""

    public fun bindAuthorizationCodeUrl(state: String): Url {
        val url = URLBuilder("https://osu.ppy.sh/oauth/authorize")
        url.parameters.apply {
            append("response_type", "code")
            append("client_id", config.clientId.toString())
            append("redirect_uri", redirectUri)
            append("scope", SCOPE)
            append("state", state)
        }
        this.state = state
        return url.build()
    }

    public suspend fun me(): OsuUser {
        return http.get("https://osu.ppy.sh/api/v2/me").body()
    }

    public suspend fun search(
        keyword: String,
        mode: GameMode = GameMode.Osu,
        category: String? = null,
        isRecommended: Boolean = false
    ): BeatmapSetSearch {
        return http.get("https://osu.ppy.sh/api/v2/beatmapsets/search") {
            parameter("q", keyword)
            parameter("m", mode.ordinal)
            parameter("s", category)
            parameter("c", if (isRecommended) "recommended" else null)
        }.body()
    }
}