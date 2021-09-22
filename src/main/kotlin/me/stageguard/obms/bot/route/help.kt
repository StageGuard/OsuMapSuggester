package me.stageguard.obms.bot.route

import me.stageguard.obms.PluginConfig
import me.stageguard.obms.bot.MessageRoute.atReply
import me.stageguard.obms.bot.RouteLock.routeLock
import net.mamoe.mirai.event.GroupMessageSubscribersBuilder

fun GroupMessageSubscribersBuilder.help() {
    routeLock(startWithIgnoreCase(".help")) {
        atReply("""See: ${PluginConfig.helpLink}""")
    }
}