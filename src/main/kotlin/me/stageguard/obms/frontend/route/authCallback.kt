package me.stageguard.obms.frontend.route

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.database.Database
import me.stageguard.obms.database.model.OsuUserInfo
import me.stageguard.obms.database.model.User
import me.stageguard.obms.osu.api.oauth.AuthCachePool
import me.stageguard.obms.osu.api.oauth.AuthType
import me.stageguard.obms.osu.api.oauth.OAuthResult
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.error
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.toList
import java.time.LocalDateTime
import java.time.ZoneOffset

const val AUTH_CALLBACK_PATH = "authCallback"

fun Application.authCallback() {
    routing {
        get("/$AUTH_CALLBACK_PATH") {
            val verified = context.request.queryParameters.run {
                OAuthManager.verifyOAuthResponse(state = get("state"), code = get("code"))
            }

            if(verified is OAuthResult.Succeed) {
                val type = AuthType.getEnumByValue(verified.state[0].toInt())
                when(type.value) {
                    AuthType.BIND_ACCOUNT.value -> {
                        val userQq = AuthCachePool.getQQ(verified.state[1])
                        AuthCachePool.removeTokenCache(verified.state[1])
                        val groupBind = verified.state[2].toLong()
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
                                    MessageRoute.sendFriendMessage(verified.state[1].toLong(), buildMessageChain {
                                        add("Successfully bind your qq to osu! account ${verified.userResponse.username}(${verified.userResponse.id}).")
                                    })
                                } else {
                                    MessageRoute.sendGroupMessage(verified.state[2].toLong(), buildMessageChain {
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
                        context.respond(HttpStatusCode.NotFound, "Not implemented.")
                    }
                    AuthType.UNKNOWN.value -> {
                        context.respond(HttpStatusCode.NotFound, "Unknown auth type: $type")
                    }
                }
            } else {
                "Exception in binding account:  ${(verified as OAuthResult.Failed).exception}".also {
                    context.respond(HttpStatusCode.InternalServerError, it)
                    OsuMapSuggester.logger.error(it)
                    verified.exception.printStackTrace()
                }

            }
        }
    }
}