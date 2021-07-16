package me.stageguard.obms.frontend.route

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import me.stageguard.obms.api.osu.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute
import me.stageguard.obms.OsuMapSuggester
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.info

const val AUTH_CALLBACK_PATH = "authCallback"

fun Application.authCallback() {
    routing {
        get("/$AUTH_CALLBACK_PATH") {
            val parameters = context.request.queryParameters
            val verifyResult = parameters.run {
                OAuthManager.verifyOAuthResponse(state = get("state"), code = get("code"))
            }

            if(verifyResult.isSuccess) {
                val get = verifyResult.getOrNull()!!
                context.respond(HttpStatusCode.OK, "Successfully bind your qq ${get.first.qq} account ${get.first.osuName}(${get.first.osuId})")
                MessageRoute.sendGroupMessage(get.second, buildMessageChain {
                    OsuMapSuggester.botInstance.groups[get.second] ?.getMember(get.first.qq).also {
                        if(it != null) add(At(it))
                    }
                    add(" Successfully bind your osu! account ${get.first.osuName}(${get.first.osuId})")
                })

            } else {
                context.respond(HttpStatusCode.InternalServerError, "Error: ${verifyResult.exceptionOrNull()}")
            }
            finish()
        }
    }
}