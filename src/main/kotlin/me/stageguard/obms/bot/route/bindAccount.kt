package me.stageguard.obms.bot.route

import me.stageguard.obms.api.osu.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute.atReply
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder

val DEVELOPING_DEBUG = System.getProperty("me.stageguard.obms.developing_debug", false.toString()).toBoolean()

fun GroupMessageSubscribersBuilder.bindAccount() {
    startsWith(".bind") {
        if(DEVELOPING_DEBUG) {
            atReply("Bind account is unavailable in debug mode.")
            return@startsWith
        }
        val link = OAuthManager.createOAuthLink(sender.id, group.id)
        atReply("Please click this oAuth link to bind your osu! account: $link")
    }
}