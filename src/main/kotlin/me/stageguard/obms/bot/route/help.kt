package me.stageguard.obms.bot.route

import kotlinx.coroutines.runInterruptible
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.PluginConfig
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock.routeLock
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder
import net.mamoe.mirai.message.data.toMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File

fun GroupMessageSubscribersBuilder.help() {
    routeLock(startsWith(".help")) {
        atReply("""See: ${PluginConfig.helpLink}""")
    }
}