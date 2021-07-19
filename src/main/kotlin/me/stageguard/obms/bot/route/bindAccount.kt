package me.stageguard.obms.bot.route

import me.stageguard.obms.api.osu.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute.atReply
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder

fun GroupMessageSubscribersBuilder.bindAccount() {
    startsWith("./bind") {
        val link = OAuthManager.createOAuthLink(sender.id, group.id)
        atReply("Please click this oAuth link to bind your osu! account: $link")
    }
}