package me.stageguard.obms.osu.api.oauth

import me.stageguard.obms.PluginConfig
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.User
import me.stageguard.obms.frontend.route.AUTH_CALLBACK_PATH
import me.stageguard.obms.database.Database
import me.stageguard.obms.utils.SimpleEncryptionUtils
import net.mamoe.mirai.utils.Either.Companion.rightOrThrow
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
        append("${PluginConfig.osuAuth.authCallbackBaseUrl}/$AUTH_CALLBACK_PATH")

        append("&response_type=code")
        append("&scope=identify%20%20friends.read%20%20public")

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
    suspend fun verifyOAuthResponse(state: String?, code: String?): Result<BindResult> {
        require(state != null) { return Result.failure(IllegalArgumentException("NULL_PARAMETER:state")) }
        require(code != null) { return Result.failure(IllegalArgumentException("NULL_PARAMETER:code")) }
        return try {
            val decrypted = SimpleEncryptionUtils.aesDecrypt(state, key).split("/")
            val qq = AuthCachePool.getQQ(decrypted[0])
            val tokenResponse = OsuWebApi.getTokenWithCode(code).rightOrThrow
            val userResponse = OsuWebApi.getSelfProfileAfterVerifyToken(tokenResponse.accessToken).rightOrThrow
            AuthCachePool.removeTokenCache(decrypted[0])
            Database.suspendQuery {
                val find = User.find { OsuUserInfo.qq eq qq }
                if(find.empty()) {
                    User.new {
                        osuId = userResponse.id
                        osuName = userResponse.username
                        this.qq = qq
                        token = tokenResponse.accessToken
                        refreshToken = tokenResponse.refreshToken
                        tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + tokenResponse.expiresIn
                    }
                    BindResult.BindSuccessful(qq, decrypted[1].toLong(), userResponse.id, userResponse.username)
                } else {
                    val existUser = find.single()
                    if(existUser.osuId == userResponse.id) {
                        existUser.token = tokenResponse.accessToken
                        existUser.refreshToken = tokenResponse.refreshToken
                        existUser.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + tokenResponse.expiresIn
                        BindResult.AlreadyBound(qq, decrypted[1].toLong(), userResponse.id, userResponse.username)
                    } else {
                        val oldOsuId = existUser.osuId
                        val oldOsuName = existUser.osuName
                        existUser.osuId = userResponse.id
                        existUser.osuName = userResponse.username
                        existUser.token = tokenResponse.accessToken
                        existUser.refreshToken = tokenResponse.refreshToken
                        existUser.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + tokenResponse.expiresIn
                        BindResult.ChangeBinding(
                            qq,
                            decrypted[1].toLong(),
                            userResponse.id,
                            userResponse.username,
                            oldOsuId,
                            oldOsuName
                        )
                    }

                }
            }.run {
                if(this == null) {
                    Result.failure(IllegalStateException("DATABASE_ERROR"))
                } else {
                    Result.success(this)
                }
            }
        } catch(ex: Exception) {
            Result.failure(IllegalStateException("INTERNAL_ERROR:$ex"))
        }
    }

    suspend fun refreshTokenInNeedAndGet(qq: Long): Result<String> = Database.suspendQuery {
        User.find { OsuUserInfo.qq eq qq }.runCatching {
            if (empty()) {
                Result.failure(IllegalStateException("NOT_BIND"))
            } else {
                val item = single()
                if (item.tokenExpireUnixSecond < LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)) {
                    val response = OsuWebApi.refreshToken(item.refreshToken).rightOrThrow
                    item.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + response.expiresIn
                    item.refreshToken = response.refreshToken
                    item.token = response.accessToken
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