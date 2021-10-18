package me.stageguard.obms.osu.api.oauth

import me.stageguard.obms.*
import me.stageguard.obms.bot.rightOrThrowLeft
import me.stageguard.obms.osu.api.OsuWebApi
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.User
import me.stageguard.obms.database.Database
import me.stageguard.obms.frontend.route.AUTHORIZE_PATH
import me.stageguard.obms.utils.Either
import me.stageguard.obms.utils.Either.Companion.mapLeft
import me.stageguard.obms.utils.SimpleEncryptionUtils
import me.stageguard.obms.utils.Either.Companion.rightOrThrow
import me.stageguard.obms.utils.InferredOptionalValue
import me.stageguard.obms.utils.OptionalValue
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
                val tokenResponse = OsuWebApi.getTokenWithCode(code).rightOrThrowLeft()
                val userResponse = OsuWebApi.getSelfProfileAfterVerifyToken(tokenResponse.accessToken).rightOrThrowLeft()
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

    suspend fun getBindingToken(qq: Long): OptionalValue<String> = Database.query { db ->
        db.sequenceOf(OsuUserInfo).filter { u -> u.qq eq qq }.toList().runCatching {
            if (isEmpty()) {
                Either(NotBindException(qq))
            } else {
                InferredOptionalValue(single().updateToken().token)
            }
        }.getOrElse {
            if (it is RefactoredException) {
                Either(it)
            } else {
                Either(UnhandledException(it))
            }
        }
    }!!

    suspend fun User.updateToken() : User {
        if (tokenExpireUnixSecond < LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)) {
            val response = OsuWebApi.refreshToken(refreshToken).mapLeft {
                if(it is BadResponseException && it.respondText.contains("401")) {
                    InvalidTokenException(this.qq)
                } else it
            }.rightOrThrowLeft()
            tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + response.expiresIn
            refreshToken = response.refreshToken
            token = response.accessToken
            flushChanges()
        }
        return this
    }
}