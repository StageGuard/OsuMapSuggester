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

    private val MAPPING = "0123456789abcdef".toByteArray()
    // to ensure that every link can only be clicked once
    private val cache: MutableSet<String> = mutableSetOf()

    private fun generateCache() = buildString {
        repeat(16) { append(MAPPING.random()) }
    }

    fun createOAuthLink(type: AuthType, additionalData: List<Any>): String = buildString {

        append("${PluginConfig.osuAuth.authCallbackBaseUrl}/$AUTHORIZE_PATH?")
        append("state=")
        append(SimpleEncryptionUtils.aesEncrypt(buildString {
            append(type.value)
            append(":")
            append(generateCache().also { cache.add(it) })
            append(":")
            append(additionalData.joinToString("/"))
        }, key).run {
            URLEncoder.encode(this, Charset.forName("UTF-8"))
        })
    }

    //Response from frontend oauth callback
    suspend fun verifyOAuthResponse(state: String?, code: String?): OAuthResult {
        require(state != null) { return OAuthResult.Failed(IllegalArgumentException("Parameter \"state\" is missing.")) }
        require(code != null) { return OAuthResult.Failed(IllegalArgumentException("Parameter \"code\" is missing.")) }
        return try {
            val decrypted = SimpleEncryptionUtils.aesDecrypt(
                state.replace(" ", "+"), key
            ).split(":")
            if(cache.remove(decrypted[1])) {
                val tokenResponse = OsuWebApi.getTokenWithCode(code).rightOrThrow
                val userResponse = OsuWebApi.getSelfProfileAfterVerifyToken(tokenResponse.accessToken).rightOrThrow
                OAuthResult.Succeed(
                    decrypted.first().toInt(),
                    decrypted.drop(2).joinToString(":").split("/"),
                    tokenResponse, userResponse
                )
            } else {
                OAuthResult.Failed(IllegalStateException("Invalid link."))
            }
        } catch(ex: Exception) {
            OAuthResult.Failed(IllegalStateException("Internal error:$ex"))
        }
    }

    suspend fun getBindingToken(qq: Long): Result<String> = Database.query { db ->
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