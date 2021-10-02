package me.stageguard.obms.frontend.route

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.User
import me.stageguard.obms.database.model.WebVerification
import me.stageguard.obms.database.model.WebVerificationStore
import me.stageguard.obms.frontend.dto.WebVerificationResponseDTO
import me.stageguard.obms.osu.api.oauth.AuthType
import me.stageguard.obms.osu.api.oauth.OAuthResult
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.buildMessageChain
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.toList
import java.time.LocalDateTime
import java.time.ZoneOffset

const val AUTH_CALLBACK_PATH = "authCallback"

@OptIn(ExperimentalSerializationApi::class)
fun Application.authCallback() {
    routing {
        get("/$AUTH_CALLBACK_PATH") {
            val verified = context.request.queryParameters.run {
                OAuthManager.verifyOAuthResponse(state = get("state"), code = get("code"))
            }

            if(verified is OAuthResult.Succeed) try {
                val type = AuthType.getEnumByValue(verified.type)
                when(type.value) {
                    AuthType.BIND_ACCOUNT.value -> {
                        val userQq = verified.additionalData[0].toLong()
                        val groupBind = verified.additionalData[1].toLong()
                        Database.query { db ->
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
                                    MessageRoute.sendFriendMessage(userQq, buildMessageChain {
                                        add("Successfully bind your qq to osu! account ${verified.userResponse.username}(${verified.userResponse.id}).")
                                    })
                                } else {
                                    MessageRoute.sendGroupMessage(userQq, buildMessageChain {
                                        OsuMapSuggester.botInstance.groups[groupBind] ?.getMember(userQq).also {
                                            if(it != null) add(At(it))
                                        }
                                        add(" Successfully bind your qq to osu! account ${verified.userResponse.username}(${verified.userResponse.id}).")
                                    })
                                }
                            } else {
                                val existUser = find.single()
                                if(existUser.osuId == verified.userResponse.id) {
                                    existUser.token = verified.tokenResponse.accessToken
                                    existUser.refreshToken = verified.tokenResponse.refreshToken
                                    existUser.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + verified.tokenResponse.expiresIn
                                    existUser.flushChanges()
                                    context.respond(HttpStatusCode.OK, "Successfully updated oAuth token of ${verified.userResponse.username}(${verified.userResponse.id}).")

                                } else {
                                    val oldOsuId = existUser.osuId
                                    val oldOsuName = existUser.osuName
                                    existUser.osuId = verified.userResponse.id
                                    existUser.osuName = verified.userResponse.username
                                    existUser.token = verified.tokenResponse.accessToken
                                    existUser.refreshToken = verified.tokenResponse.refreshToken
                                    existUser.tokenExpireUnixSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + verified.tokenResponse.expiresIn
                                    existUser.flushChanges()
                                    context.respond(HttpStatusCode.OK, "Successfully change your osu! account binding from $oldOsuName($oldOsuId) to ${verified.userResponse.username}(${verified.userResponse.id}).")
                                    if(groupBind == -1L) {
                                        MessageRoute.sendFriendMessage(userQq, buildMessageChain {
                                            add("Successfully change your osu! account binding from $oldOsuName($oldOsuId) to ${verified.userResponse.username}(${verified.userResponse.id}).")
                                        })
                                    } else {
                                        MessageRoute.sendGroupMessage(groupBind, buildMessageChain {
                                            OsuMapSuggester.botInstance.groups[groupBind] ?.getMember(userQq).also {
                                                if(it != null) add(At(it))
                                            }
                                            add(" Successfully change your osu! account binding from $oldOsuName($oldOsuId) to ${verified.userResponse.username}(${verified.userResponse.id}).")
                                        })
                                    }
                                }
                            }
                        }
                    }
                    AuthType.EDIT_RULESET.value -> {
                        val querySequence = Database.query { db ->
                            //todo: 如果多个账号qq账号绑定了同一个osu账号，那么find找到的第一个不一定是真正的目标用户
                            val userInfo = db.sequenceOf(OsuUserInfo).find { it.osuId eq verified.userResponse.id }
                            val webUserInfo = db.sequenceOf(WebVerificationStore).find {
                                it.osuId eq verified.userResponse.id
                            }
                            // 随便找一个独一无二的字符串当作 token (
                            val webToken = verified.tokenResponse.accessToken.takeLast(64)

                            if(webUserInfo != null) {
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
                            context.response.cookies.append("token", webToken)
                            context.respondRedirect(
                                PluginConfig.osuAuth.authCallbackBaseUrl + verified.additionalData.single()
                            )
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
                    OsuMapSuggester.logger.error(it)
                    ex.printStackTrace()
                }
            } else {
                "Authorization Failed: ${(verified as OAuthResult.Failed).exception}".also {
                    context.respond(HttpStatusCode.InternalServerError, it)
                    OsuMapSuggester.logger.error(it)
                    verified.exception.printStackTrace()
                }
            }
            finish()
        }
    }
}