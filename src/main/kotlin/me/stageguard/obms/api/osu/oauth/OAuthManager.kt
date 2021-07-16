package me.stageguard.obms.api.osu.oauth

import me.stageguard.obms.PluginConfig
import me.stageguard.obms.api.osu.OsuWebApi
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.User
import me.stageguard.obms.frontend.route.AUTH_CALLBACK_PATH
import me.stageguard.obms.database.Database
import me.stageguard.obms.utils.SimpleEncryptionUtils
import me.stageguard.obms.utils.success
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneOffset

object OAuthManager {

    private const val key = "c93b1la01b50b0x1"

    fun createOAuthLink(qq: Long, relatedGroup: Long = -1): String = buildString {
        append("https://osu.ppy.sh/oauth/authorize?")

        append("client_id=")
        append(PluginConfig.osuAuth.clientId)

        append("&redirect_uri=")
        @Suppress("HttpUrlsUsage")
        append("http://${PluginConfig.frontend.authCallbackShow}:${PluginConfig.frontend.port}/$AUTH_CALLBACK_PATH")

        append("&response_type=code")
        append("&scope=identify%20%20friends.read")

        append("&state=")
        append(SimpleEncryptionUtils.aesEncrypt(buildString {
            append(AuthCachePool.generateToken(qq))
            append("/")
            append(relatedGroup)
        }, key).run {
            URLEncoder.encode(this, Charset.forName("UTF-8"))
        })
    }

    //Response from frontend oauth callback
    suspend fun verifyOAuthResponse(state: String?, code: String?): Result<Pair<User, Long>> {
        require(state != null) { return Result.failure(IllegalArgumentException("NULL_PARAMETER:state")) }
        require(code != null) { return Result.failure(IllegalArgumentException("NULL_PARAMETER:code")) }
        return try {
            val decrypted = SimpleEncryptionUtils.aesDecrypt(state, key).split("/")
            val qq = AuthCachePool.getQQ(decrypted[0])
            val tokenResponse = OsuWebApi.getTokenWithCode(code).getOrThrow()
            val userResponse = OsuWebApi.getSelfProfileAfterVerifyToken(tokenResponse.accessToken).getOrThrow()
            val created = Database.suspendQuery {
                User.new {
                    osuId = userResponse.id
                    osuName = userResponse.username
                    this.qq = qq
                    token = tokenResponse.accessToken
                    refreshToken = tokenResponse.refreshToken
                    tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + tokenResponse.expiresIn
                }
            }
            AuthCachePool.removeTokenCache(decrypted[0])
            if(created == null) {
                Result.failure(IllegalStateException("DATABASE_ERROR"))
            } else {
                Result.success(Pair(created, decrypted[1].toLong()))
            }
        } catch(ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun refreshTokenInNeedAndGet(qq: Long): Result<String> = Database.suspendQuery {
        User.find { OsuUserInfo.qq eq qq }.runCatching {
            if (empty()) {
                Result.failure(IllegalStateException("NOT_BIND"))
            } else {
                val item = single()
                if (item.tokenExpireUnixSecond < LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)) {
                    val response = OsuWebApi.refreshToken(item.refreshToken).getOrThrow()
                    item.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + response.expiresIn
                    item.refreshToken = response.refreshToken
                    item.token = response.accessToken
                    Result.success(response.accessToken)
                } else {
                    Result.success(item.token)
                }
            }
        }.getOrElse {
            Result.failure(it)
        }
    }!!
}