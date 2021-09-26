package me.stageguard.obms.osu.api.oauth

import me.stageguard.obms.PluginConfig
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.User
import me.stageguard.obms.database.Database
import me.stageguard.obms.frontend.route.AUTHORIZE_PATH
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.SimpleEncryptionUtils
import me.stageguard.obms.utils.Either.Companion.rightOrThrow
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.toList
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneOffset

object OAuthManager {

    private const val key = "c93b1la01b50b0x1"

    fun createOAuthLink(qq: Long, relatedGroup: Long = -1, type: AuthType): String = buildString {
        append("${PluginConfig.osuAuth.authCallbackBaseUrl}/$AUTHORIZE_PATH?")
        append("state=")
        append(SimpleEncryptionUtils.aesEncrypt(buildString {
            append(type.value)
            append("/")
            append(AuthCachePool.generateToken(qq))
            append("/")
            append(relatedGroup)
        }, key).run {
            URLEncoder.encode(this, Charset.forName("UTF-8"))
        })
    }

    //Response from frontend oauth callback
    suspend fun verifyOAuthResponse(state: String?, code: String?): OAuthResult {
        require(state != null) { return OAuthResult.Failed(IllegalArgumentException("Parameter \"state\" is missing.")) }
        require(code != null) { return OAuthResult.Failed(IllegalArgumentException("Parameter \"code\" is missing.")) }
        return try {
            val decrypted = SimpleEncryptionUtils.aesDecrypt(state.replace(" ", "+"), key).split("/")
            AuthCachePool.getQQ(decrypted[1])
            val tokenResponse = OsuWebApi.getTokenWithCode(code).rightOrThrow
            val userResponse = OsuWebApi.getSelfProfileAfterVerifyToken(tokenResponse.accessToken).rightOrThrow
            OAuthResult.Succeed(decrypted, tokenResponse, userResponse)
        } catch(ex: Exception) {
            OAuthResult.Failed(IllegalStateException("INTERNAL_ERROR:$ex"))
        }
    }

    suspend fun refreshTokenInNeedAndGet(qq: Long): Result<String> = Database.query { db ->
        db.sequenceOf(OsuUserInfo).filter { u -> u.qq eq qq }.toList().runCatching {
            if (isEmpty()) {
                Result.failure(IllegalStateException("NOT_BIND"))
            } else {
                val item = single()
                if (item.tokenExpireUnixSecond < LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)) {
                    val response = OsuWebApi.refreshToken(item.refreshToken).rightOrThrow
                    item.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + response.expiresIn
                    item.refreshToken = response.refreshToken
                    item.token = response.accessToken
                    item.flushChanges()
                    Result.success(response.accessToken)
                } else {
                    Result.success(item.token)
                }
            }
        }.getOrElse {
            Result.failure(IllegalStateException("INTERNAL_ERROR:$it"))
        }
    }!!
}