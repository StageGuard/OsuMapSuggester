package me.stageguard.obms.frontend.route

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import me.stageguard.obms.api.osu.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.api.osu.oauth.BindResult
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.buildMessageChain

const val AUTH_CALLBACK_PATH = "authCallback"

fun Application.authCallback() {
    routing {
        get("/$AUTH_CALLBACK_PATH") {
            val parameters = context.request.queryParameters
            val verifyResult = parameters.run {
                OAuthManager.verifyOAuthResponse(state = get("state"), code = get("code"))
            }

            if(verifyResult.isSuccess) {
                when(val get = verifyResult.getOrNull()!!) {
                    is BindResult.BindSuccessful -> {
                        context.respond(HttpStatusCode.OK, "Successfully bind your qq ${get.qq} account to osu! account ${get.osuName}(${get.osuId}).")
                        if(get.groupBind == -1L) {
                            MessageRoute.sendFriendMessage(get.qq, buildMessageChain {
                                add("Successfully bind your qq to osu! account ${get.osuName}(${get.osuId}).")
                            })
                        } else {
                            MessageRoute.sendGroupMessage(get.groupBind, buildMessageChain {
                                OsuMapSuggester.botInstance.groups[get.groupBind] ?.getMember(get.qq).also {
                                    if(it != null) add(At(it))
                                }
                                add(" Successfully bind your qq to osu! account ${get.osuName}(${get.osuId}).")
                            })
                        }
                    }
                    is BindResult.ChangeBinding -> {
                        context.respond(HttpStatusCode.OK, "Successfully change your osu! account binding from ${get.oldOsuName}(${get.oldOsuId}) to ${get.osuName}(${get.osuId}) of qq ${get.qq}.")
                        if(get.groupBind == -1L) {
                            MessageRoute.sendFriendMessage(get.qq, buildMessageChain {
                                add("Successfully change your osu! account binding from ${get.oldOsuName}(${get.oldOsuId}) to ${get.osuName}(${get.osuId}).")
                            })
                        } else {
                            MessageRoute.sendGroupMessage(get.groupBind, buildMessageChain {
                                OsuMapSuggester.botInstance.groups[get.groupBind] ?.getMember(get.qq).also {
                                    if(it != null) add(At(it))
                                }
                                add(" Successfully change your osu! account binding from ${get.oldOsuName}(${get.oldOsuId}) to ${get.osuName}(${get.osuId}).")
                            })
                        }
                    }
                    is BindResult.AlreadyBound -> {
                        context.respond(HttpStatusCode.Forbidden, "You have already bound your qq to ${get.osuName}(${get.osuId}). Please do not bind repeatedly.")
                    }
                }
            } else {
                context.respond(HttpStatusCode.InternalServerError, "Error: ${verifyResult.exceptionOrNull()}")
            }
            finish()
        }
    }
}