package me.stageguard.obms.bot

import kotlinx.coroutines.launch
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.bot.route.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.subscribeGroupMessages
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.info

object MessageRoute {
    fun subscribeMessages(bot: Bot) {
        bot.eventChannel.subscribeGroupMessages {
            bindAccount()
            bestPerformanceAnalyze()
            help()
            skill()
            recentScore()
        }
        OsuMapSuggester.logger.info { "Subscribed group and friend messages." }
    }
    fun sendGroupMessage(group: Long, message: Message) = OsuMapSuggester.launch {
        OsuMapSuggester.botInstance.groups[group] ?.sendMessage(message)
    }
    fun sendFriendMessage(friend: Long, message: Message) = OsuMapSuggester.launch {
        OsuMapSuggester.botInstance.friends[friend] ?.sendMessage(message)
    }
    suspend fun GroupMessageEvent.atReply(plain: String) =
        group.sendMessage(buildMessageChain {
            add(At(sender))
            add(" $plain")
        })
    suspend fun GroupMessageEvent.atReply(msg: MessageChain) =
        group.sendMessage(buildMessageChain {
            add(At(sender))
            add(msg)
        })
}