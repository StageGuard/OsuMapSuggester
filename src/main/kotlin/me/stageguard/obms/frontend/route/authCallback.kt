package me.stageguard.obms.frontend.route

import com.mikuac.shiro.common.utils.MsgUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.User
import me.stageguard.obms.database.model.WebVerification
import me.stageguard.obms.database.model.WebVerificationStore
import me.stageguard.obms.frontend.dto.WebVerificationResponseDTO
import me.stageguard.obms.osu.api.oauth.AuthType
import me.stageguard.obms.osu.api.oauth.OAuthResult
import me.stageguard.obms.utils.info
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.toList
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneOffset

const val AUTH_CALLBACK_PATH = "authCallback"

private val logger = LoggerFactory.getLogger("AuthCallbackRoute")

@OptIn(ExperimentalSerializationApi::class)
fun Application.authCallback(oAuthManager: OAuthManager, database: Database, authCallbackBaseUrl: String) {
    routing {
        get("/$AUTH_CALLBACK_PATH") {
            val verified = context.request.queryParameters.run {
                oAuthManager.verifyOAuthResponse(state = get("state"), code = get("code"))
            }

            if(verified is OAuthResult.Succeed) try {
                val type = AuthType.getEnumByValue(verified.type)
                when(type.value) {
                    AuthType.BIND_ACCOUNT.value -> {
                        val userQq = verified.additionalData[0].toLong()
                        val groupBind = verified.additionalData[1].toLong()
                        database.query { db ->
                            val find = db.sequenceOf(OsuUserInfo).filter { u -> u.qq eq userQq}.toList()
                            if(find.isEmpty()) {
                                OsuUserInfo.insert(User {
                                    osuId = verified.userResponse.id
                                    osuName = verified.userResponse.username
                                    this.qq = userQq
                                    token = verified.tokenResponse.accessToken
                                    refreshToken = verified.tokenResponse.refreshToken
                                    tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + verified.tokenResponse.expiresIn
                                })
                                context.respond(HttpStatusCode.OK,
                                    "Successfully bind your qq $userQq account to osu! account ${verified.userResponse.username}(${verified.userResponse.id})."
                                )
                                if(groupBind == -1L) {
                                    MessageRoute.sendFriendMessage(userQq, MsgUtils.builder().text(
                                        "Successfully bind your qq to osu! account ${verified.userResponse.username}(${verified.userResponse.id})."
                                    ).build())
                                } else {

                                    MessageRoute.sendGroupMessage(groupBind, MsgUtils.builder().at(userQq).text(
                                        " Successfully bind your qq to osu! account ${verified.userResponse.username}(${verified.userResponse.id})."
                                    ).build())
                                }
                                logger.info { "New user bind: qq $userQq to osu ${verified.userResponse.username}(${verified.userResponse.id})." }
                            } else {
                                val existUser = find.single()

                                existUser.token = verified.tokenResponse.accessToken
                                existUser.refreshToken = verified.tokenResponse.refreshToken
                                existUser.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + verified.tokenResponse.expiresIn

                                if(existUser.osuId == verified.userResponse.id) {
                                    existUser.flushChanges()
                                    context.respond(HttpStatusCode.OK, "Successfully updated oAuth token of ${verified.userResponse.username}(${verified.userResponse.id}).")
                                } else {
                                    val oldOsuId = existUser.osuId
                                    val oldOsuName = existUser.osuName
                                    existUser.osuId = verified.userResponse.id
                                    existUser.osuName = verified.userResponse.username
                                    existUser.flushChanges()
                                    context.respond(HttpStatusCode.OK, "Successfully change your osu! account binding from $oldOsuName($oldOsuId) to ${verified.userResponse.username}(${verified.userResponse.id}).")
                                    if(groupBind == -1L) {
                                        MessageRoute.sendFriendMessage(userQq, MsgUtils.builder().text(
                                            "Successfully change your osu! account binding from $oldOsuName($oldOsuId) to ${verified.userResponse.username}(${verified.userResponse.id})."
                                        ).build())
                                    } else {
                                        MessageRoute.sendGroupMessage(groupBind, MsgUtils.builder().at(userQq).text(
                                            " Successfully change your osu! account binding from $oldOsuName($oldOsuId) to ${verified.userResponse.username}(${verified.userResponse.id})."
                                        ).build())
                                    }
                                }
                                logger.info { "User change binding: qq $userQq to osu ${verified.userResponse.username}(${verified.userResponse.id})." }
                            }
                        }
                    }
                    AuthType.EDIT_RULESET.value -> {
                        val querySequence = database.query { db ->
                            //todo: 如果多个账号qq账号绑定了同一个osu账号，那么find找到的第一个不一定是真正的目标用户
                            val userInfo = db.sequenceOf(OsuUserInfo).find { it.osuId eq verified.userResponse.id }
                            val webUserInfo = db.sequenceOf(WebVerificationStore).find {
                                it.osuId eq verified.userResponse.id
                            }
                            // 随便找一个独一无二的字符串当作 token (
                            val webToken = verified.tokenResponse.accessToken.takeLast(64)

                            if(webUserInfo != null) {
                                webUserInfo.qq = userInfo ?.qq ?: -1
                                webUserInfo.token = webToken
                                webUserInfo.osuId = verified.userResponse.id
                                webUserInfo.flushChanges()
                            } else {
                                WebVerificationStore.insert(WebVerification {
                                    qq = userInfo ?.qq ?: -1
                                    osuId = verified.userResponse.id
                                    token = webToken
                                })
                            }
                            context.response.cookies.append("token", webToken, maxAge = 1000L * 60 * 60 * 24 * 365 * 10)
                            context.respondRedirect(
                                authCallbackBaseUrl + verified.additionalData.single()
                            )
                            logger.info { "Web user authorized: osu ${verified.userResponse.username}(${verified.userResponse.id})." }
                            return@query
                        }
                        if(querySequence == null) context.respond(HttpStatusCode.InternalServerError,
                            Json.encodeToString(WebVerificationResponseDTO(-1,
                                errorMessage = "Internal error: Database is disconnected from server."
                            )
                        ))
                    }
                    AuthType.UNKNOWN.value -> {
                        context.respond(HttpStatusCode.NotFound, "Unknown auth type: $type")
                    }
                }
            } catch (ex: Exception) {
                "Exception in processing authorization request: $ex".also {
                    context.respond(HttpStatusCode.InternalServerError, it)
                    logger.error(it)
                }
            } else {
                "Authorization Failed: ${(verified as OAuthResult.Failed).exception.outgoingMessage}".also {
                    context.respond(HttpStatusCode.InternalServerError, it)
                    logger.error(verified.exception.toString())
                }
            }
            finish()
        }
    }
}
