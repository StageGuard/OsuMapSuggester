package me.stageguard.obms.bot

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.BotContainer
import com.mikuac.shiro.dto.action.common.MsgId
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import jakarta.annotation.Resource
import me.stageguard.obms.OsuMapSuggester
import me.stageguard.obms.utils.warning
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
object MessageRoute {
    private val logger = LoggerFactory.getLogger(this::class.java)

    lateinit var botContainer: BotContainer
    private val triggerCache = mutableSetOf<Long>()
    private val botQq by lazy { OsuMapSuggester.botQq }

    fun sendGroupMessage(group: Long, message: String) {
        val bot = botContainer.robots[botQq]
        bot?.sendGroupMsg(group, message, false)
    }
    fun sendFriendMessage(friend: Long, message: String)  {
        val bot = botContainer.robots[botQq]
        bot?.sendPrivateMsg(friend, message, false)
    }


    private fun releaseLock(sender: Long) {
        triggerCache.removeIf { it == sender }
    }
    private fun addLock(sender: Long) {
        triggerCache.add(sender)
    }

    suspend fun withLockSuspend(sender: Long, block: suspend () -> Unit) {
        addLock(sender)
        block()
        releaseLock(sender)
    }

    fun withLock(sender: Long, block: () -> Unit) {
        addLock(sender)
        block()
        releaseLock(sender)
    }

    fun GroupMessageEvent.routeLock(block: GroupMessageEvent.() -> Unit) {
        if (sender.userId in triggerCache) return
        else block()
    }

    fun GroupMessageEvent.atReply(action: MsgUtils.() -> MsgUtils): MsgId? {
        val bot = botContainer.robots[botQq]
        if (bot == null) {
            logger.warning { "bot $botQq should be present." }
            return null
        }
        val message = MsgUtils.builder().at(sender.userId).text("").action().build()
        return bot.sendGroupMsg(groupId, message, false)?.data
    }

    fun GroupMessageEvent.atReplyText(plain: String): MsgId? {
        val bot = botContainer.robots[botQq]
        if (bot == null) {
            logger.warning { "bot $botQq should be present." }
            return null
        }
        val message = MsgUtils.builder().at(sender.userId).text(" $plain").build()
        return bot.sendGroupMsg(groupId, message, false)?.data
    }


    fun GroupMessageEvent.sendMessage(message: String): MsgId? {
        val bot = botContainer.robots[botQq]
        if (bot == null) {
            logger.warning { "bot $botQq should be present." }
            return null
        }
        return bot.sendGroupMsg(groupId, message, false)?.data
    }
}

@Component
class MessageRouteInjector(@Resource private val botContainer: BotContainer) {
    init {
        MessageRoute.botContainer = botContainer
    }
}