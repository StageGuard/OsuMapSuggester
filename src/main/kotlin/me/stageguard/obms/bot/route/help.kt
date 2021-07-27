package me.stageguard.obms.bot.route

import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.api.osu.oauth.OAuthManager
import me.stageguard.obms.bot.MessageRoute.atReply
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File

fun GroupMessageSubscribersBuilder.help() {
    startsWith(".help") {
        val helpImagePath =
            OsuMapSuggester.dataFolder.absolutePath + File.separator + "resources" + File.separator + "image/help.png"

        val externalResource = File(helpImagePath).toExternalResource("png")
        val image = group.uploadImage(externalResource)
        externalResource.close()
        atReply(image.toMessageChain())
    }
}