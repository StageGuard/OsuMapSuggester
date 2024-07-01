package me.stageguard.obms.osu.api.oauth

import jakarta.annotation.Resource
import me.stageguard.obms.*
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.User
import me.stageguard.obms.database.Database
import me.stageguard.obms.frontend.route.AUTHORIZE_PATH
import me.stageguard.obms.frontend.route.AUTH_CALLBACK_PATH
import me.stageguard.obms.osu.api.OsuHttpClient
import me.stageguard.obms.osu.api.dto.GetAccessTokenRequestDTO
import me.stageguard.obms.osu.api.dto.GetAccessTokenResponseDTO
import me.stageguard.obms.osu.api.dto.GetUserDTO
import me.stageguard.obms.osu.api.dto.RefreshTokenRequestDTO
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.mapLeft
import me.stageguard.obms.utils.SimpleEncryptionUtils
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.toList
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneOffset

@Component
class OAuthManager {
    val BASE_URL_V2 = "https://osu.ppy.sh/api/v2"
    val BASE_URL_V1 = "https://osu.ppy.sh/api"
    val BASE_URL_OLD = "https://old.ppy.sh"

    @Resource
    private lateinit var osuHttpClient: OsuHttpClient
    @Resource
    private lateinit var database: Database

    @Value("\${osuAuth.authCallbackBaseUrl}")
    private lateinit var authCallbackBaseUrl: String
    @Value("\${osuAuth.clientId}") private lateinit var _clientId: String
    private val clientId by lazy { _clientId.toInt() }
    @Value("\${osuAuth.secret}") private lateinit var secret: String


    private val key = "c93b1la01b50b0x1"

    private val MAPPING = "0123456789abcdef".toByteArray()
    // to ensure that every link can only be clicked once
    private val cache: MutableSet<String> = mutableSetOf()

    private fun generateCache() = buildString {
        repeat(16) { append(MAPPING.random()) }
    }

    fun createOAuthLink(type: AuthType, additionalData: List<Any>): String = buildString {

        append("$authCallbackBaseUrl/$AUTHORIZE_PATH?")
        append("state=")
        append(SimpleEncryptionUtils.aesEncrypt(buildString {
            append(type.value)
            append(":")
            append(generateCache().also { cache.add(it) })
            append(":")
            append(additionalData.joinToString("/") {
                URLEncoder.encode(it.toString(), Charset.forName("UTF-8"))
            })
        }, key).run {
            URLEncoder.encode(this, Charset.forName("UTF-8"))
        })
    }

    //Response from frontend oauth callback
    suspend fun verifyOAuthResponse(state: String?, code: String?): OAuthResult {
        require(state != null) {
            return OAuthResult.Failed(UnhandledException(IllegalArgumentException("Parameter \"state\" is missing.")))
        }
        require(code != null) {
            return OAuthResult.Failed(UnhandledException(IllegalArgumentException("Parameter \"code\" is missing.")))
        }
        return try {
            val decrypted = SimpleEncryptionUtils.aesDecrypt(
                state.replace(" ", "+"), key
            ).split(":")
            if(cache.remove(decrypted[1])) {
                val tokenResponse = getTokenWithCode(code).rightOrThrowLeft()
                val userResponse = getSelfProfileAfterVerifyToken(tokenResponse.accessToken).rightOrThrowLeft()
                val additionalList = decrypted.drop(2).joinToString(":").split("/").map {
                    URLDecoder.decode(it, Charset.forName("UTF-8"))
                }
                OAuthResult.Succeed(decrypted.first().toInt(), additionalList, tokenResponse, userResponse)
            } else {
                OAuthResult.Failed(InvalidVerifyLinkException(decrypted[1]))
            }
        } catch(ex: Exception) {
           OAuthResult.Failed(if(ex is RefactoredException) ex else UnhandledException(ex))
        }
    }

    suspend fun getBindingToken(qq: Long): OptionalValue<String> = database.query { db ->
        db.sequenceOf(OsuUserInfo).filter { u -> u.qq eq qq }.toList().runCatching {
            if (isEmpty()) {
                Either(NotBindException(qq))
            } else {
                InferredOptionalValue(updateToken(single()).token)
            }
        }.getOrElse {
            if (it is RefactoredException) {
                Either(it)
            } else {
                Either(UnhandledException(it))
            }
        }
    }!!

    suspend fun updateToken(user: User) : User {
        if (user.tokenExpireUnixSecond < LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)) {
            val response = refreshToken(user.refreshToken).mapLeft {
                if(it is BadResponseException && (it.respondText.contains("401") || it.respondText.contains("invalid"))) {
                    InvalidTokenException(user.qq)
                } else it
            }.rightOrThrowLeft()
            user.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + response.expiresIn
            user.refreshToken = response.refreshToken
            user.token = response.accessToken
            user.flushChanges()
        }
        return user
    }





    suspend fun getTokenWithCode(
        code: String
    ): OptionalValue<GetAccessTokenResponseDTO> = osuHttpClient.post(
        url = "https://osu.ppy.sh/oauth/token",
        token = null,
        body = GetAccessTokenRequestDTO(
            clientId = clientId,
            clientSecret = secret,
            grantType = "authorization_code",
            code = code,
            redirectUri = "${authCallbackBaseUrl}/$AUTH_CALLBACK_PATH"
        )
    )

    suspend fun refreshToken(
        refToken: String
    ): OptionalValue<GetAccessTokenResponseDTO> = osuHttpClient.post(
        url = "https://osu.ppy.sh/oauth/token",
        token = null,
        body = RefreshTokenRequestDTO(
            clientId = clientId,
            clientSecret = secret,
            grantType = "refresh_token",
            refreshToken = refToken,
            redirectUri = "${authCallbackBaseUrl}/$AUTH_CALLBACK_PATH"
        )
    )

    suspend fun getSelfProfileAfterVerifyToken(
        token: String
    ) = osuHttpClient.get<String, GetUserDTO>(
        url = "$BASE_URL_V2/me",
        parameters = mapOf(),
        headers = mapOf("Authorization" to "Bearer $token")
    ) { osuHttpClient.json.decodeFromString(this) }
}

