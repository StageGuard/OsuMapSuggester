package me.stageguard.obms.frontend.route

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import me.stageguard.obms.osu.api.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.osu.api.oauth.BindResult
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.buildMessageChain

const val AUTH_CALLBACK_PATH = "authCallback"

fun Application.authCallback() {
    routing {
        get("/$AUTH_CALLBACK_PATH") {
            context.request.queryParameters.run {
                OAuthManager.verifyOAuthResponse(state = get("state"), code = get("code"))
            }.onSuccess { get ->
                when(get) {
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
                        context.respond(HttpStatusCode.OK, "Successfully updated oAuth token of ${get.osuName}(${get.osuId}).")
                    }
                }
            }.onFailure {
                context.respond(HttpStatusCode.InternalServerError, "Error: $it")
            }
            finish()
        }
    }
}